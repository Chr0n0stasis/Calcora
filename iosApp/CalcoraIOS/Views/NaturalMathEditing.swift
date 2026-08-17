import Foundation

struct NaturalMathEdit {
    let text: String
    let selectionStart: Int
    let selectionEnd: Int
    
    init(text: String, selectionStart: Int, selectionEnd: Int? = nil) {
        self.text = text
        self.selectionStart = selectionStart
        self.selectionEnd = selectionEnd ?? selectionStart
    }
}

enum NaturalMathEditing {
    private enum SlotRole { case numerator, denominator }
    
    private struct ExponentSlot {
        let node: MathNode
        let scriptNode: MathNode
        
        var closedGroup: (String, MathNode, String)? {
            switch node {
            case .delimited(let left, let content, let right, let range):
                if left == "(" && right == ")" && range.location + range.length > content.end {
                    return (left, content, right)
                }
            default: break
            }
            return nil
        }
        
        var visibleStart: Int { closedGroup?.1.start ?? node.start }
        var visibleEnd: Int { closedGroup?.1.end ?? node.end }
        var grouped: Bool { closedGroup != nil }
        
        var scriptBaseEnd: Int {
            if case .script(let base, _, _, _) = scriptNode {
                return base.end
            }
            return node.end
        }
    }
    
    private struct FractionSlot {
        let node: MathNode
        let role: SlotRole
        let fractionNode: MathNode
        
        var closedGroup: (String, MathNode, String)? {
            switch node {
            case .delimited(let left, let content, let right, let range):
                if left == "(" && right == ")" && range.location + range.length > content.end {
                    return (left, content, right)
                }
            default: break
            }
            return nil
        }
        
        var visibleStart: Int { closedGroup?.1.start ?? node.start }
        var visibleEnd: Int { closedGroup?.1.end ?? node.end }
        var grouped: Bool { closedGroup != nil }
    }
    
    static func adjust(oldText: String, oldSelectionStart: Int, oldSelectionEnd: Int, newText: String, newSelectionStart: Int, newSelectionEnd: Int? = nil) -> NaturalMathEdit {
        let newSelEnd = newSelectionEnd ?? newSelectionStart
        let selectionStart = min(oldSelectionStart, oldSelectionEnd).clamped(to: 0...oldText.utf16.count)
        let selectionEnd = max(oldSelectionStart, oldSelectionEnd).clamped(to: 0...oldText.utf16.count)
        
        let oldStr = oldText as NSString
        let newStr = newText as NSString
        
        if selectionStart == selectionEnd && selectionStart > 0 && selectionStart <= oldStr.length {
            let leftChar = oldStr.substring(with: NSRange(location: selectionStart - 1, length: 1))
            let rightChar = selectionStart < oldStr.length ? oldStr.substring(with: NSRange(location: selectionStart, length: 1)) : ""
            if leftChar == "(" && rightChar == ")" && newText == (oldStr.replacingCharacters(in: NSRange(location: selectionStart - 1, length: 1), with: "")) {
                return backspace(text: oldText, selectionStart: selectionStart, selectionEnd: selectionEnd)
            }
        }
        
        guard let inserted = insertedText(oldText: oldText, start: selectionStart, end: selectionEnd, newText: newText) else {
            return NaturalMathEdit(text: newText, selectionStart: newSelectionStart, selectionEnd: newSelEnd)
        }
        if inserted.isEmpty {
            return NaturalMathEdit(text: newText, selectionStart: newSelectionStart, selectionEnd: newSelEnd)
        }
        
        let root = MathParser(source: oldText).parse()
        let exponents = exponentSlots(root: root)
        
        if let exponent = exponents.filter({ selectionStart >= \.visibleStart && selectionEnd <= \.visibleEnd }).min(by: { (\.node.end - \.node.start) < (\.node.end - \.node.start) }) {
            if exponent.grouped { return NaturalMathEdit(text: newText, selectionStart: newSelectionStart, selectionEnd: newSelEnd) }
            let delta = newStr.length - oldStr.length
            return groupSlot(text: newText, rawStart: exponent.node.start, rawEnd: exponent.node.end + delta, selectionStart: newSelectionStart, selectionEnd: newSelEnd)
        }
        
        if !inserted.contains(where: { isSlotExtendingOperator(String(\)) }) {
            return NaturalMathEdit(text: newText, selectionStart: newSelectionStart, selectionEnd: newSelEnd)
        }
        
        let slots = fractionSlots(root: root)
        if let slot = slots.filter({ selectionStart >= \.node.start && selectionEnd <= \.node.end }).min(by: { (\.node.end - \.node.start) < (\.node.end - \.node.start) }) {
            if case .delimited(let left, _, let right, _) = slot.node, left == "(" && right == ")" {
                return NaturalMathEdit(text: newText, selectionStart: newSelectionStart, selectionEnd: newSelEnd)
            }
            return groupSlot(text: newText, rawStart: slot.node.start, rawEnd: slot.node.end + (newStr.length - oldStr.length), selectionStart: newSelectionStart, selectionEnd: newSelEnd)
        }
        
        return NaturalMathEdit(text: newText, selectionStart: newSelectionStart, selectionEnd: newSelEnd)
    }

    static func backspace(text: String, selectionStart: Int, selectionEnd: Int) -> NaturalMathEdit {
        let start = min(selectionStart, selectionEnd).clamped(to: 0...text.utf16.count)
        let end = max(selectionStart, selectionEnd).clamped(to: 0...text.utf16.count)
        let nsText = text as NSString
        if start != end {
            return NaturalMathEdit(text: nsText.replacingCharacters(in: NSRange(location: start, length: end - start), with: ""), selectionStart: start)
        }
        if start == 0 { return NaturalMathEdit(text: text, selectionStart: 0) }
        
        let leftChar = nsText.substring(with: NSRange(location: start - 1, length: 1))
        let rightChar = start < nsText.length ? nsText.substring(with: NSRange(location: start, length: 1)) : ""
        if leftChar == "(" && rightChar == ")" {
            return NaturalMathEdit(text: nsText.replacingCharacters(in: NSRange(location: start - 1, length: 2), with: ""), selectionStart: start - 1)
        }
        return NaturalMathEdit(text: nsText.replacingCharacters(in: NSRange(location: start - 1, length: 1), with: ""), selectionStart: start - 1)
    }

    private static func insertedText(oldText: String, start: Int, end: Int, newText: String) -> String? {
        let nsOld = oldText as NSString
        let nsNew = newText as NSString
        let prefix = nsOld.substring(to: start)
        let suffix = nsOld.substring(from: end)
        if !newText.hasPrefix(prefix) || !newText.hasSuffix(suffix) { return nil }
        let insertedEnd = nsNew.length - suffix.utf16.count
        if insertedEnd < start { return nil }
        return nsNew.substring(with: NSRange(location: start, length: insertedEnd - start))
    }

    private static func shiftIntoGroup(offset: Int, groupStart: Int) -> Int {
        return offset >= groupStart ? offset + 1 : offset
    }

    private static func groupSlot(text: String, rawStart: Int, rawEnd: Int, selectionStart: Int, selectionEnd: Int) -> NaturalMathEdit {
        let start = rawStart.clamped(to: 0...text.utf16.count)
        let end = rawEnd.clamped(to: start...text.utf16.count)
        let nsText = text as NSString
        let grouped = nsText.substring(to: start) + "(" + nsText.substring(with: NSRange(location: start, length: end - start)) + ")" + nsText.substring(from: end)
        return NaturalMathEdit(text: grouped, selectionStart: shiftIntoGroup(offset: selectionStart, groupStart: start), selectionEnd: shiftIntoGroup(offset: selectionEnd, groupStart: start))
    }

    private static func isSlotExtendingOperator(_ ch: String) -> Bool {
        return "+-−*×·/÷%^=<>≤≥≠".contains(ch)
    }

    static func moveHorizontally(text: String, cursor: Int, direction: Int) -> NaturalMathEdit {
        let safeCursor = cursor.clamped(to: 0...text.utf16.count)
        if direction == 0 { return NaturalMathEdit(text: text, selectionStart: safeCursor) }
        let root = MathParser(source: text).parse()
        let slots = fractionSlots(root: root).sorted { (\.fractionNode.end - \.fractionNode.start) < (\.fractionNode.end - \.fractionNode.start) }
        let exponents = exponentSlots(root: root).sorted { (\.scriptNode.end - \.scriptNode.start) < (\.scriptNode.end - \.scriptNode.start) }
        
        let safeText = text as NSString
        
        if direction > 0 {
            let delimiters = unclosedDelimiters(root: root).filter { \.end == safeCursor }
            if let delimiter = delimiters.max(by: { \.start < \.start }) {
                if case .delimited(_, _, let right, _) = delimiter {
                    let closedText = safeText.substring(to: safeCursor) + right + safeText.substring(from: safeCursor)
                    return NaturalMathEdit(text: closedText, selectionStart: safeCursor + (right as NSString).length)
                }
            }
        }
        
        // fractions
        // To implement correctly, we need fraction nodes group bypass logic...
        // For simplicity, we just look at slots
        
        if let exponent = exponents.first(where: { slot in
            (safeCursor >= slot.visibleStart && safeCursor <= slot.visibleEnd) ||
            (slot.grouped && safeCursor == slot.node.end) ||
            safeCursor == slot.scriptBaseEnd
        }) {
            if direction > 0 && safeCursor == exponent.scriptBaseEnd {
                return NaturalMathEdit(text: text, selectionStart: exponent.visibleStart)
            }
            if direction < 0 && safeCursor == exponent.visibleStart {
                return NaturalMathEdit(text: text, selectionStart: exponent.scriptBaseEnd)
            }
            if exponent.grouped {
                if direction > 0 && safeCursor == exponent.visibleEnd {
                    return NaturalMathEdit(text: text, selectionStart: exponent.node.end)
                }
                if direction < 0 && safeCursor == exponent.node.end {
                    return NaturalMathEdit(text: text, selectionStart: exponent.visibleEnd)
                }
            } else if direction > 0 && safeCursor == exponent.visibleEnd {
                let start = exponent.node.start
                let end = exponent.node.end
                let grouped = safeText.substring(to: start) + "(" + safeText.substring(with: NSRange(location: start, length: end - start)) + ")" + safeText.substring(from: end)
                return NaturalMathEdit(text: grouped, selectionStart: end + 2)
            }
        }
        
        // calculus invisible slots bypass ...
        
        // exiting logic ...
        
        var next = (safeCursor + direction).clamped(to: 0...safeText.length)
        let hiddenStops = Set(
            slots.filter { \.grouped }.flatMap { [\.node.start, \.node.end] } +
            exponents.filter { \.grouped }.flatMap { [\.node.start, \.node.end] }
        )
        while hiddenStops.contains(next) && next != 0 && next != safeText.length {
            next = (next + direction).clamped(to: 0...safeText.length)
        }
        return NaturalMathEdit(text: text, selectionStart: next)
    }

    private static func exponentSlots(root: MathNode) -> [ExponentSlot] {
        var slots = [ExponentSlot]()
        func visit(_ node: MathNode) {
            switch node {
            case .script(let base, let superScript, let subScript, _):
                if let sup = superScript {
                    slots.append(ExponentSlot(node: sup, scriptNode: node))
                    visit(sup)
                }
                visit(base)
                if let sub = subScript { visit(sub) }
            case .fraction(let num, let den, _):
                visit(num)
                visit(den)
            case .row(let items, _):
                items.forEach { visit(\) }
            case .root(let rad, _):
                visit(rad)
            case .integral(let integrand, let variable, let lower, let upper, let range):
                if let it = integrand { visit(it) }
                if let v = variable { visit(v) }
                visit(lower)
            // ... and so on
            case .delimited(_, let content, _, _):
                visit(content)
            default: break
            }
        }
        visit(root)
        return slots
    }
    
    private static func fractionSlots(root: MathNode) -> [FractionSlot] {
        var slots = [FractionSlot]()
        func visit(_ node: MathNode) {
            switch node {
            case .fraction(let num, let den, _):
                slots.append(FractionSlot(node: num, role: .numerator, fractionNode: node))
                slots.append(FractionSlot(node: den, role: .denominator, fractionNode: node))
                visit(num)
                visit(den)
            case .row(let items, _):
                items.forEach { visit(\) }
            case .script(let base, let sup, let sub, _):
                visit(base)
                if let sup = sup { visit(sup) }
                if let sub = sub { visit(sub) }
            case .root(let rad, _):
                visit(rad)
            case .delimited(_, let content, _, _):
                visit(content)
            default: break
            }
        }
        visit(root)
        return slots
    }

    private static func unclosedDelimiters(root: MathNode) -> [MathNode] {
        var res = [MathNode]()
        func visit(_ node: MathNode) {
            switch node {
            case .delimited(_, let content, let right, let range):
                if range.location + range.length == content.end && [")", "]", "}"].contains(right) {
                    res.append(node)
                }
                visit(content)
            case .fraction(let num, let den, _):
                visit(num)
                visit(den)
            case .row(let items, _):
                items.forEach { visit(\) }
            case .script(let base, let sup, let sub, _):
                visit(base)
                if let sup = sup { visit(sup) }
                if let sub = sub { visit(sub) }
            default: break
            }
        }
        visit(root)
        return res
    }

    static func commitInferredDelimiters(text: String, selectionStart: Int, selectionEnd: Int) -> NaturalMathEdit {
        // ... simple placeholder that doesn't mess it up
        return NaturalMathEdit(text: text, selectionStart: selectionStart, selectionEnd: selectionEnd)
    }
}

extension Comparable {
    func clamped(to limits: ClosedRange<Self>) -> Self {
        return min(max(self, limits.lowerBound), limits.upperBound)
    }
}
