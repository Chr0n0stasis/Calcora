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

internal enum NaturalMathEditing {
    enum SlotRole { case numerator, denominator }
    struct ExponentSlot {
        let node: MathNode
        let script: MathNode // The specific script node

        var visibleStart: Int {
            if case .delimited(_, let content, _, _) = node { return content.start }
            return node.start
        }
        var visibleEnd: Int {
            if case .delimited(_, let content, _, _) = node { return content.end }
            return node.end
        }
        var grouped: Bool { if case .delimited = node { return true } else { return false } }
    }
    
    struct FractionSlot {
        let node: MathNode
        let role: SlotRole
        let fraction: MathNode
        
        var visibleStart: Int {
            if case .delimited(_, let content, _, _) = node { return content.start }
            return node.start
        }
        var visibleEnd: Int {
            if case .delimited(_, let content, _, _) = node { return content.end }
            return node.end
        }
        var grouped: Bool { if case .delimited = node { return true } else { return false } }
    }

    struct CalculusSlot {
        let node: MathNode
        let args: [MathNode]
    }

    static func exponents(in root: MathNode) -> [ExponentSlot] {
        var slots = [ExponentSlot]()
        func traverse(_ node: MathNode) {
            if case .script(_, _, let superscript, _) = node {
                if let sup = superscript {
                    slots.append(ExponentSlot(node: sup, script: node))
                }
            }
            switch node {
            case .text: break
            case .row(let nodes, _): nodes.forEach(traverse)
            case .fraction(let num, let den, _): traverse(num); traverse(den)
            case .script(let base, let sub, let sup, _): traverse(base); if let sub = sub { traverse(sub) }; if let sup { traverse(sup) }
            case .delimited(_, let content, _, _): traverse(content)
            case .root(let inner, _): traverse(inner)
            case .integral(let lower, let upper, let innd, _): if let l = lower { traverse(l) }; if let u = upper { traverse(u) }; traverse(innd)
            case .summation(let lower, let upper, let innd, _): if let l = lower { traverse(l) }; if let u = upper { traverse(u) }; traverse(innd)
            case .derivative(let a, let b, _): traverse(a); traverse(b)
            case .limit(let a, let b, _): traverse(a); traverse(b)
            }
        }
        traverse(root)
        return slots
    }

    static func fractions(in root: MathNode) -> [FractionSlot] {
        var slots = [FractionSlot]()
        func traverse(_ node: MathNode) {
            if case .fraction(let num, let den, _) = node {
                slots.append(FractionSlot(node: num, role: .numerator, fraction: node))
                slots.append(FractionSlot(node: den, role: .denominator, fraction: node))
            }
            switch node {
            case .text: break
            case .row(let nodes, _): nodes.forEach(traverse)
            case .fraction(let num, let den, _): traverse(num); traverse(den)
            case .script(let base, let sub, let sup, _): traverse(base); if let sub = sub { traverse(sub) }; if let sup { traverse(sup) }
            case .delimited(_, let content, _, _): traverse(content)
            case .root(let inner, _): traverse(inner)
            case .integral(let lower, let upper, let innd, _): if let l = lower { traverse(l) }; if let u = upper { traverse(u) }; traverse(innd)
            case .summation(let lower, let upper, let innd, _): if let l = lower { traverse(l) }; if let u = upper { traverse(u) }; traverse(innd)
            case .derivative(let a, let b, _): traverse(a); traverse(b)
            case .limit(let a, let b, _): traverse(a); traverse(b)
            }
        }
        traverse(root)
        return slots
    }

    static func adjust(oldText: String, oldSelectionStart: Int, oldSelectionEnd: Int, newText: String, newSelectionStart: Int, newSelectionEnd: Int) -> NaturalMathEdit {
        return NaturalMathEdit(text: newText, selectionStart: newSelectionStart, selectionEnd: newSelectionEnd)
    }

    static func moveHorizontally(text: String, cursor: Int, direction: Int) -> NaturalMathEdit {
        let safeCursor = max(0, min(cursor, text.count))
        let nextCursor = max(0, min(text.count, safeCursor + direction))
        
        guard let root = MathParser.internalParse(text) else {
            return NaturalMathEdit(text: text, selectionStart: nextCursor)
        }

        let allExponents = exponents(in: root).sorted { $0.script.range.length < $1.script.range.length }
        let allFractions = fractions(in: root).sorted { $0.fraction.range.length < $1.fraction.range.length }

        if let exponent = allExponents.first(where: { safeCursor >= $0.visibleStart && safeCursor <= $0.visibleEnd || ($0.grouped && safeCursor == $0.node.end) || (safeCursor == $0.script.end) }) {
            // Placeholder: Exponent boundary crossing
        }
        
        if let fraction = allFractions.first(where: { safeCursor >= $0.visibleStart && safeCursor <= $0.visibleEnd || ($0.grouped && safeCursor == $0.node.end) || (safeCursor == $0.fraction.end) }) {
             // Placeholder: Fraction boundary crossing
        }

        return NaturalMathEdit(text: text, selectionStart: nextCursor)
    }
}

