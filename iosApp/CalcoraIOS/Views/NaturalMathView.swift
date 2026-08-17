import Foundation
import SwiftUI
import UIKit
import WebKit

// MARK: - Public SwiftUI wrapper

struct NaturalMathInputView: UIViewRepresentable {
    @Binding var text: String
    @Binding var selectedRange: NSRange?
    var fontSize: CGFloat = 26
    var syntaxHighlighting: Bool = true
    var onCommit: ((String) -> Void)? = nil

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> NaturalMathDrawingView {
        let view = NaturalMathDrawingView()
        view.delegate = context.coordinator
        view.fontSize = fontSize
        view.syntaxHighlighting = syntaxHighlighting
        view.text = text
        view.selectedRange = selectedRange ?? NSRange(location: text.utf16.count, length: 0)
        return view
    }

    func updateUIView(_ view: NaturalMathDrawingView, context: Context) {
        view.fontSize = fontSize
        view.syntaxHighlighting = syntaxHighlighting
        if view.text != text { view.text = text }
        if let selectedRange, view.selectedRange != selectedRange, !view.isEditingMath {
            view.selectedRange = selectedRange
        }
    }

    final class Coordinator: NSObject, NaturalMathDrawingViewDelegate {
        var parent: NaturalMathInputView
        init(_ parent: NaturalMathInputView) { self.parent = parent }

        func naturalMathDrawingViewDidChangeText(_ view: NaturalMathDrawingView) {
            parent.text = view.text
        }
        func naturalMathDrawingViewDidChangeSelection(_ view: NaturalMathDrawingView) {
            parent.selectedRange = view.selectedRange
        }
        func naturalMathDrawingViewDidCommit(_ view: NaturalMathDrawingView) {
            parent.onCommit?(view.text)
        }
    }
}

// MARK: - Existing editor sheet, now backed by the 2-D editor

struct NaturalMathEditorView: View {
    @EnvironmentObject private var store: CalcoraStore
    @Environment(\.dismiss) private var dismiss
    @State private var text: String
    @State private var selectedRange: NSRange?

    init(initialText: String) {
        _text = State(initialValue: initialText)
    }

    private let templates = [
        ("Fraction", "()/()"),
        ("Power", "()^()"),
        ("Root", "sqrt()"),
        ("Integral", "integrate(,x)"),
        ("Derivative", "diff(,x)"),
        ("Sum", "sum(,k,1,n)")
    ]

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                NaturalMathInputView(
                    text: $text,
                    selectedRange: $selectedRange,
                    fontSize: 27,
                    onCommit: { store.expression = $0; dismiss() }
                )
                .frame(minHeight: 150, maxHeight: 260)
                .padding(4)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(.secondary.opacity(0.25)))
                .padding(.horizontal)

                Text(LocalizedStringKey("Insert a structured template. Gray squares are editable placeholders."))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(templates, id: \.0) { template in
                            Button(template.0) { insert(template.1) }
                                .buttonStyle(.bordered)
                        }
                    }
                    .padding(.horizontal)
                }

                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 92), spacing: 8)], spacing: 8) {
                        ForEach(["√", "π", "∞", "≤", "≥", "≠", "∑", "∫", "→", "()"], id: \.self) { token in
                            Button(token) { insert(token == "→" ? "->" : token) }
                                .font(.system(size: 20, design: .monospaced))
                                .frame(maxWidth: .infinity, minHeight: 42)
                                .background(.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                        }
                    }
                    .padding(.horizontal)
                }
                Spacer(minLength: 0)
            }
            .padding(.top)
            .navigationTitle(LocalizedStringKey("Natural Math"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(LocalizedStringKey("Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(LocalizedStringKey("Use")) { store.expression = text; dismiss() }
                        .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func insert(_ token: String) {
        let range = selectedRange ?? NSRange(location: text.utf16.count, length: 0)
        let cursor = token.firstIndex(of: "(").map { token.distance(from: token.startIndex, to: $0) + 1 } ?? token.utf16.count
        guard let swiftRange = Range(range, in: text) else {
            text += token
            selectedRange = NSRange(location: text.utf16.count - token.utf16.count + cursor, length: 0)
            return
        }
        text.replaceSubrange(swiftRange, with: token)
        selectedRange = NSRange(location: range.location + cursor, length: 0)
    }
}

// MARK: - UIKit drawing view

protocol NaturalMathDrawingViewDelegate: AnyObject {
    func naturalMathDrawingViewDidChangeText(_ view: NaturalMathDrawingView)
    func naturalMathDrawingViewDidChangeSelection(_ view: NaturalMathDrawingView)
    func naturalMathDrawingViewDidCommit(_ view: NaturalMathDrawingView)
}

final class NaturalMathDrawingView: UIView, UITextViewDelegate {
    weak var delegate: NaturalMathDrawingViewDelegate?

    var text: String {
        didSet {
            guard !isUpdatingTextView else { return }
            isUpdatingTextView = true
            hiddenTextView.text = text
            isUpdatingTextView = false
            setNeedsLayout()
            setNeedsDisplay()
        }
    }

    var selectedRange: NSRange {
        didSet {
            guard !isUpdatingTextView else { return }
            let maxLocation = text.utf16.count
            let safeLocation = min(selectedRange.location, maxLocation)
            let safeLength = min(selectedRange.length, maxLocation - safeLocation)
            let safeRange = NSRange(location: safeLocation, length: safeLength)
            isUpdatingTextView = true
            hiddenTextView.selectedRange = safeRange
            isUpdatingTextView = false
            setNeedsDisplay()
        }
    }

    var fontSize: CGFloat = 26 { didSet { setNeedsLayout(); setNeedsDisplay() } }
    var syntaxHighlighting = true { didSet { setNeedsDisplay() } }
    var isEditingMath: Bool { hiddenTextView.isFirstResponder }

    private let hiddenTextView = UITextView()
    private var isUpdatingTextView = false
    private var layout = MathLayout.zero

    init(text: String = "", selectedRange: NSRange = NSRange(location: 0, length: 0)) {
        self.text = text
        self.selectedRange = selectedRange
        super.init(frame: .zero)
        isOpaque = false
        backgroundColor = .clear
        contentMode = .redraw
        setupHiddenTextView()
        setupGestures()
        rebuildLayout()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        rebuildLayout()
    }

    override func draw(_ rect: CGRect) {
        super.draw(rect)
        guard let context = UIGraphicsGetCurrentContext() else { return }
        context.saveGState()
        let originX: CGFloat = 10
        let originY = max(4, (bounds.height - layout.height) / 2)
        context.translateBy(x: originX, y: originY)

        for run in layout.runs {
            run.draw()
        }
        for box in layout.boxes {
            box.draw()
        }
        for line in layout.lines {
            line.draw()
        }

        if selectedRange.length == 1, let box = layout.boxes.first(where: { $0.offset == selectedRange.location }) {
            let path = UIBezierPath(roundedRect: box.frame, cornerRadius: 5)
            UIColor.systemBlue.withAlphaComponent(0.55).setStroke()
            path.lineWidth = 1.5
            path.stroke()
        }

        if selectedRange.length == 0, let caret = layout.carets.min(by: { abs($0.offset - selectedRange.location) < abs($1.offset - selectedRange.location) }) {
            let path = UIBezierPath()
            path.move(to: CGPoint(x: caret.x, y: caret.top))
            path.addLine(to: CGPoint(x: caret.x, y: caret.bottom))
            path.lineWidth = 2
            UIColor.systemBlue.setStroke()
            path.stroke()
        }

        context.restoreGState()
    }

    override func becomeFirstResponder() -> Bool { hiddenTextView.becomeFirstResponder() }

    private func setupHiddenTextView() {
        hiddenTextView.frame = CGRect(x: 0, y: 0, width: 1, height: 1)
        hiddenTextView.backgroundColor = .clear
        hiddenTextView.textColor = .clear
        hiddenTextView.tintColor = .clear
        hiddenTextView.font = UIFont.systemFont(ofSize: 1)
        hiddenTextView.textContainerInset = .zero
        hiddenTextView.autocorrectionType = .no
        hiddenTextView.autocapitalizationType = .none
        hiddenTextView.smartQuotesType = .no
        hiddenTextView.smartDashesType = .no
        hiddenTextView.smartInsertDeleteType = .no
        hiddenTextView.returnKeyType = .done
        hiddenTextView.delegate = self
        hiddenTextView.alpha = 0.02
        hiddenTextView.isUserInteractionEnabled = true
        addSubview(hiddenTextView)
    }

    private func setupGestures() {
        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        addGestureRecognizer(tap)

        let left = UISwipeGestureRecognizer(target: self, action: #selector(handleSwipe(_:)))
        left.direction = .left
        addGestureRecognizer(left)

        let right = UISwipeGestureRecognizer(target: self, action: #selector(handleSwipe(_:)))
        right.direction = .right
        addGestureRecognizer(right)
        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        pan.minimumNumberOfTouches = 1
        pan.maximumNumberOfTouches = 1
        pan.cancelsTouchesInView = false
        addGestureRecognizer(pan)
    }

    private func rebuildLayout() {
        layout = MathTypesetter.layout(
            source: text,
            fontSize: fontSize,
            syntaxHighlighting: syntaxHighlighting
        )
        setNeedsDisplay()
    }

    @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
        let point = gesture.location(in: self)
        let localX = point.x - 10
        let localY = point.y - max(4, (bounds.height - layout.height) / 2)
        if let box = layout.boxes.first(where: { $0.frame.insetBy(dx: -7, dy: -7).contains(CGPoint(x: localX, y: localY)) }) {
            let offset = box.offset
            selectedRange = NSRange(location: offset, length: 0)
            hiddenTextView.becomeFirstResponder()
            return
        }
        let caret = layout.carets.min {
            abs($0.x - localX) + abs((($0.top + $0.bottom) / 2) - localY) * 1.4
                < abs($1.x - localX) + abs((($1.top + $1.bottom) / 2) - localY) * 1.4
        }
        let offset = caret?.offset ?? text.utf16.count
        selectedRange = NSRange(location: offset, length: 0)
        hiddenTextView.becomeFirstResponder()
    }

    @objc private func handleSwipe(_ gesture: UISwipeGestureRecognizer) {
        guard hiddenTextView.isFirstResponder else { return }
        let current = selectedRange
        if gesture.direction == .left {
            if current.length > 0 {
                selectedRange = NSRange(location: current.location, length: 0)
            } else if current.location > 0 {
                selectedRange = NSRange(location: current.location - 1, length: 0)
            }
        } else {
            if current.length > 0 {
                selectedRange = NSRange(location: NSMaxRange(current), length: 0)
            } else if current.location < text.utf16.count {
                selectedRange = NSRange(location: current.location + 1, length: 0)
            }
        }
    }

    @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
        guard hiddenTextView.isFirstResponder else { return }
        let point = gesture.location(in: self)
        let localX = point.x - 10
        guard let caret = layout.carets.min(by: { abs($0.x - localX) < abs($1.x - localX) }) else { return }
        selectedRange = NSRange(location: caret.offset, length: 0)
    }

    func textViewDidChange(_ textView: UITextView) {
        guard !isUpdatingTextView else { return }
        isUpdatingTextView = true
        text = textView.text
        isUpdatingTextView = false
        delegate?.naturalMathDrawingViewDidChangeText(self)
        rebuildLayout()
    }

    func textViewDidChangeSelection(_ textView: UITextView) {
        guard !isUpdatingTextView else { return }
        isUpdatingTextView = true
        selectedRange = textView.selectedRange
        isUpdatingTextView = false
        delegate?.naturalMathDrawingViewDidChangeSelection(self)
        setNeedsDisplay()
    }

    func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText replacement: String) -> Bool {
        if replacement == "\n" {
            delegate?.naturalMathDrawingViewDidCommit(self)
            return false
        }
        return true
    }
}

// MARK: - Math node model

internal indirect enum MathNode {
    case text(String, NSRange)
    case row([MathNode], NSRange)
    case fraction(MathNode, MathNode, NSRange)
    case script(MathNode, MathNode?, MathNode?, NSRange)
    case delimited(String, MathNode, String, NSRange)
    case root(MathNode, NSRange)
    case integral(MathNode?, MathNode?, MathNode, NSRange)
    case summation(MathNode?, MathNode?, MathNode, NSRange)
    case derivative(MathNode, MathNode, NSRange)
    case limit(MathNode, MathNode, NSRange)

    var start: Int { range.location }
    var end: Int { range.location + range.length }

    var range: NSRange {
        switch self {
        case .text(_, let r): return r
        case .row(_, let r): return r
        case .fraction(_, _, let r): return r
        case .script(_, _, _, let r): return r
        case .delimited(_, _, _, let r): return r
        case .root(_, let r): return r
        case .integral(_, _, _, let r): return r
        case .summation(_, _, _, let r): return r
        case .derivative(_, _, let r): return r
        case .limit(_, _, let r): return r
        }
    }
}

// MARK: - Layout primitives

private internal struct MathRun {
    let string: String
    let font: UIFont
    let color: UIColor
    let frame: CGRect

    func draw() {
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        (string as NSString).draw(at: frame.origin, withAttributes: attributes)
    }
}

private struct MathBox {
    let frame: CGRect
    let offset: Int

    func draw() {
        let path = UIBezierPath(roundedRect: frame, cornerRadius: 4)
        UIColor.secondaryLabel.setFill()
        path.fill()
    }
}

private struct MathLine {
    let frame: CGRect
    let color: UIColor

    func draw() {
        let path = UIBezierPath()
        path.move(to: CGPoint(x: frame.minX, y: frame.midY))
        path.addLine(to: CGPoint(x: frame.maxX, y: frame.midY))
        color.setStroke()
        path.lineWidth = 1
        path.stroke()
    }
}

private struct MathCaret {
    let offset: Int
    let x: CGFloat
    let top: CGFloat
    let bottom: CGFloat
}

private internal struct MathLayout {
    var width: CGFloat = 0
    var height: CGFloat = 0
    var baseline: CGFloat = 0
    var runs: [MathRun] = []
    var boxes: [MathBox] = []
    var lines: [MathLine] = []
    var carets: [MathCaret] = []
    static let zero = MathLayout()
}

// MARK: - Simplified typesetter

private enum MathTypesetter {
    static func layout(source: String, fontSize: CGFloat, syntaxHighlighting: Bool) -> MathLayout {
        guard !source.isEmpty else {
            var empty = MathLayout()
            empty.height = fontSize
            empty.carets = [MathCaret(offset: 0, x: 0, top: 0, bottom: fontSize)]
            return empty
        }
        let ns = source as NSString
        let node = MathParser.parse(ns, NSRange(location: 0, length: ns.length))
        let painter = Painter(fontSize: fontSize, syntaxHighlighting: syntaxHighlighting)
        return painter.layout(node)
    }
}

private final class Painter {
    let fontSize: CGFloat
    let syntaxHighlighting: Bool

    init(fontSize: CGFloat, syntaxHighlighting: Bool) {
        self.fontSize = fontSize
        self.syntaxHighlighting = syntaxHighlighting
    }

    func layout(_ node: MathNode) -> MathLayout {
        var result = layoutNode(node, scale: 1)
        result.width += 2
        return result
    }

    private func layoutNode(_ node: MathNode, scale: CGFloat) -> MathLayout {
        switch node {
        case let .text(value, range):
            return textLayout(value, range: range, scale: scale)
        case let .row(children, range):
            return rowLayout(children, range: range, scale: scale)
        case let .fraction(numerator, denominator, range):
            return fractionLayout(numerator, denominator, range: range)
        case let .script(base, sup, sub, range):
            return scriptLayout(base: base, sup: sup, sub: sub, range: range)
        case let .delimited(left, content, right, range):
            return delimitedLayout(left: left, content: content, right: right, range: range)
        case let .root(content, range):
            return rootLayout(content: content, range: range)
        case let .integral(lower, upper, body, range):
            return integralLayout(lower: lower, upper: upper, body: body, range: range)
        case let .summation(lower, upper, body, range):
            return summationLayout(lower: lower, upper: upper, body: body, range: range)
        case let .derivative(body, variable, range):
            return derivativeLayout(body: body, variable: variable, range: range)
        case let .limit(body, lower, range):
            return limitLayout(body: body, lower: lower, range: range)
        }
    }

    private func textLayout(_ value: String, range: NSRange, scale: CGFloat) -> MathLayout {
        var layout = MathLayout()
        let size = fontSize * scale
        let font = UIFont.monospacedSystemFont(ofSize: size, weight: .regular)
        let color = Self.color(for: value, syntaxHighlighting: syntaxHighlighting)
        let ns = value as NSString

        if value.isEmpty {
            let boxWidth = max(14, size * 0.7)
            let boxHeight = max(16, size * 0.72)
            layout.width = boxWidth
            layout.height = boxHeight
            layout.baseline = font.ascender
            layout.boxes.append(MathBox(frame: CGRect(x: 0, y: 0, width: boxWidth, height: boxHeight), offset: range.location))
            layout.carets = [
                MathCaret(offset: range.location, x: 0, top: 0, bottom: boxHeight),
                MathCaret(offset: NSMaxRange(range), x: boxWidth, top: 0, bottom: boxHeight)
            ]
            return layout
        }

        let textSize = ns.size(withAttributes: [.font: font])
        layout.width = textSize.width
        layout.height = max(size, textSize.height)
        layout.baseline = font.ascender
        layout.runs.append(MathRun(string: value, font: font, color: color, frame: CGRect(x: 0, y: 0, width: textSize.width, height: textSize.height)))
        for index in 0...ns.length {
            let prefix = ns.substring(to: index)
            let width = (prefix as NSString).size(withAttributes: [.font: font]).width
            layout.carets.append(MathCaret(offset: range.location + index, x: width, top: 0, bottom: layout.height))
        }
        return layout
    }

    private func rowLayout(_ children: [MathNode], range: NSRange, scale: CGFloat) -> MathLayout {
        guard !children.isEmpty else { return MathLayout.zero }
        var result = MathLayout()
        var x: CGFloat = 0
        var maxBaseline: CGFloat = 0
        var childLayouts: [(MathLayout, CGFloat)] = []

        for child in children {
            let childLayout = layoutNode(child, scale: scale)
            childLayouts.append((childLayout, x))
            x += childLayout.width + 3
            maxBaseline = max(maxBaseline, childLayout.baseline)
        }
        result.width = max(0, x - 3)
        result.baseline = maxBaseline
        result.height = 0
        for (childLayout, childX) in childLayouts {
            let yOffset = maxBaseline - childLayout.baseline
            result.height = max(result.height, yOffset + childLayout.height)
            result.runs.append(contentsOf: childLayout.runs.map { run in
                MathRun(string: run.string, font: run.font, color: run.color, frame: run.frame.offsetBy(dx: childX, dy: yOffset))
            })
            result.lines.append(contentsOf: childLayout.lines.map { line in
                MathLine(frame: line.frame.offsetBy(dx: childX, dy: yOffset), color: line.color)
            })
            result.carets.append(contentsOf: childLayout.carets.map { caret in
                MathCaret(offset: caret.offset, x: caret.x + childX, top: caret.top + yOffset, bottom: caret.bottom + yOffset)
            })
        }
        return result
    }

    private func fractionLayout(_ numerator: MathNode, _ denominator: MathNode, range: NSRange) -> MathLayout {
        let top = layoutNode(numerator, scale: 0.78)
        let bottom = layoutNode(denominator, scale: 0.78)
        var result = MathLayout()
        let width = max(top.width, bottom.width) + 10
        let gap: CGFloat = 6
        result.width = width
        result.height = top.height + bottom.height + gap + 4
        let centerX = (width - top.width) / 2
        let centerBottom = (width - bottom.width) / 2
        result.runs.append(contentsOf: top.runs.map { run in
            MathRun(string: run.string, font: run.font, color: run.color, frame: run.frame.offsetBy(dx: centerX, dy: 0))
        })
        let bottomY = top.height + gap + 2
        result.runs.append(contentsOf: bottom.runs.map { run in
            MathRun(string: run.string, font: run.font, color: run.color, frame: run.frame.offsetBy(dx: centerBottom, dy: bottomY))
        })
        result.lines.append(MathLine(frame: CGRect(x: 0, y: top.height + gap, width: width, height: 2), color: .secondaryLabel))
        result.carets.append(MathCaret(offset: range.location, x: 0, top: 0, bottom: result.height))
        result.carets.append(contentsOf: top.carets.map { MathCaret(offset: $0.offset, x: $0.x + centerX, top: $0.top, bottom: $0.bottom) })
        result.carets.append(contentsOf: bottom.carets.map { MathCaret(offset: $0.offset, x: $0.x + centerBottom, top: $0.top + bottomY, bottom: $0.bottom + bottomY) })
        result.carets.append(MathCaret(offset: NSMaxRange(range), x: width, top: 0, bottom: result.height))
        return result
    }

    private func scriptLayout(base: MathNode, sup: MathNode?, sub: MathNode?, range: NSRange) -> MathLayout {
        let baseLayout = layoutNode(base, scale: 1)
        let supLayout = sup.map { layoutNode($0, scale: 0.66) }
        let subLayout = sub.map { layoutNode($0, scale: 0.66) }
        var result = MathLayout()
        let supWidth = supLayout?.width ?? 0
        let subWidth = subLayout?.width ?? 0
        result.width = baseLayout.width + max(supWidth, subWidth) + 5
        let baseBottom = max((supLayout?.height ?? 0) * 0.65, baseLayout.height, 20)
        result.height = baseBottom + (subLayout?.height ?? 0) * 0.72 + (supLayout?.height ?? 0) * 0.65

        result.runs.append(contentsOf: baseLayout.runs.map { run in
            MathRun(string: run.string, font: run.font, color: run.color, frame: run.frame.offsetBy(dx: 0, dy: (supLayout?.height ?? 0) * 0.65))
        })
        result.carets.append(contentsOf: baseLayout.carets.map { caret in
            MathCaret(offset: caret.offset, x: caret.x, top: caret.top + (supLayout?.height ?? 0) * 0.65, bottom: caret.bottom + (supLayout?.height ?? 0) * 0.65)
        })
        if let supLayout {
            let x = baseLayout.width + 3
            result.runs.append(contentsOf: supLayout.runs.map { run in
                MathRun(string: run.string, font: run.font, color: run.color, frame: run.frame.offsetBy(dx: x, dy: 0))
            })
            result.carets.append(contentsOf: supLayout.carets.map { MathCaret(offset: $0.offset, x: $0.x + x, top: $0.top, bottom: $0.bottom) })
        }
        if let subLayout {
            let x = baseLayout.width + 3
            let y = (supLayout?.height ?? 0) * 0.65 + baseLayout.height - subLayout.height * 0.25
            result.runs.append(contentsOf: subLayout.runs.map { run in
                MathRun(string: run.string, font: run.font, color: run.color, frame: run.frame.offsetBy(dx: x, dy: y))
            })
            result.carets.append(contentsOf: subLayout.carets.map { MathCaret(offset: $0.offset, x: $0.x + x, top: $0.top + y, bottom: $0.bottom + y) })
        }
        result.carets.append(MathCaret(offset: NSMaxRange(range), x: result.width, top: 0, bottom: result.height))
        return result
    }

    private func delimitedLayout(left: String, content: MathNode, right: String, range: NSRange) -> MathLayout {
        let contentLayout = layoutNode(content, scale: 1)
        let leftFont = UIFont.systemFont(ofSize: fontSize * 1.1, weight: .regular)
        let rightFont = leftFont
        let leftWidth = (left as NSString).size(withAttributes: [.font: leftFont]).width
        let rightWidth = (right as NSString).size(withAttributes: [.font: rightFont]).width
        var result = MathLayout()
        result.width = contentLayout.width + leftWidth + rightWidth + 4
        result.height = max(contentLayout.height, fontSize)
        result.runs.append(MathRun(string: left, font: leftFont, color: .label, frame: CGRect(x: 0, y: (result.height - fontSize) / 2, width: leftWidth, height: fontSize)))
        let contentY = (result.height - contentLayout.height) / 2
        result.runs.append(contentsOf: contentLayout.runs.map { run in
            MathRun(string: run.string, font: run.font, color: run.color, frame: run.frame.offsetBy(dx: leftWidth + 2, dy: contentY))
        })
        result.carets.append(MathCaret(offset: range.location, x: 0, top: 0, bottom: result.height))
        result.carets.append(contentsOf: contentLayout.carets.map { MathCaret(offset: $0.offset, x: $0.x + leftWidth + 2, top: $0.top + contentY, bottom: $0.bottom + contentY) })
        result.runs.append(MathRun(string: right, font: rightFont, color: .label, frame: CGRect(x: result.width - rightWidth, y: (result.height - fontSize) / 2, width: rightWidth, height: fontSize)))
        result.carets.append(MathCaret(offset: NSMaxRange(range), x: result.width, top: 0, bottom: result.height))
        return result
    }

    private func rootLayout(content: MathNode, range: NSRange) -> MathLayout {
        let contentLayout = layoutNode(content, scale: 1)
        let symbol = "√"
        let symbolFont = UIFont.systemFont(ofSize: fontSize * 1.25, weight: .regular)
        let symbolSize = (symbol as NSString).size(withAttributes: [.font: symbolFont])
        var result = MathLayout()
        result.width = symbolSize.width + contentLayout.width + 8
        result.height = max(contentLayout.height + 4, symbolSize.height)
        result.runs.append(MathRun(string: symbol, font: symbolFont, color: .label, frame: CGRect(x: 0, y: (result.height - symbolSize.height) / 2, width: symbolSize.width, height: symbolSize.height)))
        let contentX = symbolSize.width + 4
        let contentY = (result.height - contentLayout.height) / 2
        result.runs.append(contentsOf: contentLayout.runs.map { run in
            MathRun(string: run.string, font: run.font, color: run.color, frame: run.frame.offsetBy(dx: contentX, dy: contentY))
        })
        result.boxes.append(contentsOf: contentLayout.boxes.map { MathBox(frame: $0.frame.offsetBy(dx: contentX, dy: contentY), offset: $0.offset) })
        result.carets.append(MathCaret(offset: range.location, x: 0, top: 0, bottom: result.height))
        result.carets.append(contentsOf: contentLayout.carets.map { MathCaret(offset: $0.offset, x: $0.x + contentX, top: $0.top + contentY, bottom: $0.bottom + contentY) })
        result.carets.append(MathCaret(offset: NSMaxRange(range), x: result.width, top: 0, bottom: result.height))
        result.lines.append(MathLine(frame: CGRect(x: symbolSize.width, y: 0, width: contentLayout.width + 4, height: 2), color: .secondaryLabel))
        return result
    }

    private func integralLayout(lower: MathNode?, upper: MathNode?, body: MathNode, range: NSRange) -> MathLayout {
        largeOperatorLayout(symbol: "∫", lower: lower, upper: upper, body: body, range: range)
    }

    private func summationLayout(lower: MathNode?, upper: MathNode?, body: MathNode, range: NSRange) -> MathLayout {
        largeOperatorLayout(symbol: "∑", lower: lower, upper: upper, body: body, range: range)
    }

    private func largeOperatorLayout(symbol: String, lower: MathNode?, upper: MathNode?, body: MathNode, range: NSRange) -> MathLayout {
        let symbolFont = UIFont.systemFont(ofSize: fontSize * 1.55, weight: .regular)
        let symbolSize = (symbol as NSString).size(withAttributes: [.font: symbolFont])
        let upperLayout = upper.map { layoutNode($0, scale: 0.55) }
        let lowerLayout = lower.map { layoutNode($0, scale: 0.55) }
        let bodyLayout = layoutNode(body, scale: 1)
        let symbolColumnWidth = max(symbolSize.width, max(upperLayout?.width ?? 0, lowerLayout?.width ?? 0) + 6)
        let upperHeight = upperLayout?.height ?? 0
        let lowerHeight = lowerLayout?.height ?? 0
        let symbolY = upperHeight + 2
        let lowerY = symbolY + symbolSize.height + 2
        let height = lowerY + lowerHeight
        var result = MathLayout()
        result.width = symbolColumnWidth + bodyLayout.width + 8
        result.height = height
        result.baseline = symbolY + symbolSize.height * 0.5

        result.runs.append(MathRun(string: symbol, font: symbolFont, color: .label, frame: CGRect(x: (symbolColumnWidth - symbolSize.width) / 2, y: symbolY, width: symbolSize.width, height: symbolSize.height)))
        if let upperLayout {
            let x = (symbolColumnWidth - upperLayout.width) / 2
            result.runs.append(contentsOf: upperLayout.runs.map { MathRun(string: $0.string, font: $0.font, color: $0.color, frame: $0.frame.offsetBy(dx: x, dy: 0)) })
            result.boxes.append(contentsOf: upperLayout.boxes.map { MathBox(frame: $0.frame.offsetBy(dx: x, dy: 0), offset: $0.offset) })
            result.carets.append(contentsOf: upperLayout.carets.map { MathCaret(offset: $0.offset, x: $0.x + x, top: $0.top, bottom: $0.bottom) })
        }
        if let lowerLayout {
            let x = (symbolColumnWidth - lowerLayout.width) / 2
            result.runs.append(contentsOf: lowerLayout.runs.map { MathRun(string: $0.string, font: $0.font, color: $0.color, frame: $0.frame.offsetBy(dx: x, dy: lowerY)) })
            result.boxes.append(contentsOf: lowerLayout.boxes.map { MathBox(frame: $0.frame.offsetBy(dx: x, dy: lowerY), offset: $0.offset) })
            result.carets.append(contentsOf: lowerLayout.carets.map { MathCaret(offset: $0.offset, x: $0.x + x, top: $0.top + lowerY, bottom: $0.bottom + lowerY) })
        }
        let bodyX = symbolColumnWidth + 4
        let bodyY = max(0, (height - bodyLayout.height) / 2)
        result.runs.append(contentsOf: bodyLayout.runs.map { MathRun(string: $0.string, font: $0.font, color: $0.color, frame: $0.frame.offsetBy(dx: bodyX, dy: bodyY)) })
        result.boxes.append(contentsOf: bodyLayout.boxes.map { MathBox(frame: $0.frame.offsetBy(dx: bodyX, dy: bodyY), offset: $0.offset) })
        result.carets.append(contentsOf: bodyLayout.carets.map { MathCaret(offset: $0.offset, x: $0.x + bodyX, top: $0.top + bodyY, bottom: $0.bottom + bodyY) })
        result.carets.append(MathCaret(offset: range.location, x: 0, top: 0, bottom: height))
        result.carets.append(MathCaret(offset: NSMaxRange(range), x: result.width, top: 0, bottom: height))
        return result
    }

    private func derivativeLayout(body: MathNode, variable: MathNode, range: NSRange) -> MathLayout {
        let numerator = MathNode.text("d", range)
        let denominator = MathNode.text("d\(variableText(variable))", range)
        let fraction = fractionLayout(numerator, denominator, range: range)
        let bodyLayout = layoutNode(body, scale: 1)
        return appendRow(fraction, bodyLayout)
    }

    private func limitLayout(body: MathNode, lower: MathNode, range: NSRange) -> MathLayout {
        let limitSymbol = scriptLayout(base: MathNode.text("lim", range), sup: nil, sub: lower, range: range)
        let bodyLayout = layoutNode(body, scale: 1)
        return appendRow(limitSymbol, bodyLayout)
    }

    private func appendRow(_ left: MathLayout, _ right: MathLayout) -> MathLayout {
        var result = MathLayout()
        let gap: CGFloat = 6
        let maxHeight = max(left.height, right.height)
        result.width = left.width + gap + right.width
        result.height = maxHeight
        let leftY = (maxHeight - left.height) / 2
        let rightY = (maxHeight - right.height) / 2
        result.runs.append(contentsOf: left.runs.map { MathRun(string: $0.string, font: $0.font, color: $0.color, frame: $0.frame.offsetBy(dx: 0, dy: leftY)) })
        result.boxes.append(contentsOf: left.boxes.map { MathBox(frame: $0.frame.offsetBy(dx: 0, dy: leftY), offset: $0.offset) })
        result.lines.append(contentsOf: left.lines.map { MathLine(frame: $0.frame.offsetBy(dx: 0, dy: leftY), color: $0.color) })
        result.carets.append(contentsOf: left.carets.map { MathCaret(offset: $0.offset, x: $0.x, top: $0.top + leftY, bottom: $0.bottom + leftY) })
        result.runs.append(contentsOf: right.runs.map { MathRun(string: $0.string, font: $0.font, color: $0.color, frame: $0.frame.offsetBy(dx: left.width + gap, dy: rightY)) })
        result.boxes.append(contentsOf: right.boxes.map { MathBox(frame: $0.frame.offsetBy(dx: left.width + gap, dy: rightY), offset: $0.offset) })
        result.lines.append(contentsOf: right.lines.map { MathLine(frame: $0.frame.offsetBy(dx: left.width + gap, dy: rightY), color: $0.color) })
        result.carets.append(contentsOf: right.carets.map { MathCaret(offset: $0.offset, x: $0.x + left.width + gap, top: $0.top + rightY, bottom: $0.bottom + rightY) })
        return result
    }

    private func variableText(_ node: MathNode) -> String {
        if case let .text(value, _) = node { return value }
        return "x"
    }

    private static func color(for text: String, syntaxHighlighting: Bool) -> UIColor {
        guard syntaxHighlighting else { return .label }
        if text.rangeOfCharacter(from: CharacterSet.decimalDigits) != nil, text.allSatisfy({ $0.isNumber || $0 == "." || $0 == "," }) {
            return .systemBlue
        }
        if text.count > 1, text.rangeOfCharacter(from: CharacterSet.letters) != nil, text.allSatisfy({ $0.isLetter || $0.isNumber || $0 == "_" }) {
            return .systemPurple
        }
        return .label
    }
}

// MARK: - Minimal Xcas parser

private enum MathParser {
    static func parse(_ source: NSString, _ range: NSRange) -> MathNode {
        let full = source.substring(with: range)
        guard !full.isEmpty else { return .text("", range) }

        if let call = parseFunctionCall(source, range, name: "sqrt"), let arg = call.first {
            return .root(parse(source, arg), range)
        }
        if let call = parseFunctionCall(source, range, name: "integrate"), call.count >= 1 {
            let body = call[0]
            let lower = call.count >= 3 ? call[2] : nil
            let upper = call.count >= 4 ? call[3] : nil
            return .integral(lower.map { parse(source, $0) }, upper.map { parse(source, $0) }, parse(source, body), range)
        }
        if let call = parseFunctionCall(source, range, name: "sum"), call.count >= 1 {
            let body = call[0]
            let lower = call.count >= 3 ? call[2] : nil
            let upper = call.count >= 4 ? call[3] : nil
            return .summation(lower.map { parse(source, $0) }, upper.map { parse(source, $0) }, parse(source, body), range)
        }
        if let call = parseFunctionCall(source, range, name: "diff"), call.count >= 2 {
            return .derivative(parse(source, call[0]), parse(source, call[1]), range)
        }
        if let call = parseFunctionCall(source, range, name: "limit"), call.count >= 1 {
            let lower = call.count >= 2 ? call[1] : call[0]
            return .limit(parse(source, call[0]), parse(source, lower), range)
        }

        if let fraction = topLevelOperator(source, range, operator: "/") {
            return .fraction(parse(source, fraction.left), parse(source, fraction.right), range)
        }
        if let script = topLevelOperator(source, range, operator: "^") {
            return .script(parse(source, script.left), parse(source, script.right), nil, range)
        }
        if let script = topLevelOperator(source, range, operator: "_") {
            return .script(parse(source, script.left), nil, parse(source, script.right), range)
        }

        if range.length > 0, source.character(at: range.location) == 40,
           let close = matchingDelimiter(source, open: range.location), close == NSMaxRange(range) - 1 {
            let inner = NSRange(location: range.location + 1, length: max(0, close - range.location - 1))
            return .delimited("(", parse(source, inner), ")", range)
        }

        return .text(full, range)
    }

    private static func topLevelOperator(_ source: NSString, _ range: NSRange, operator op: String) -> (left: NSRange, right: NSRange)? {
        var depth = 0
        for index in range.location..<NSMaxRange(range) {
            let char = source.substring(with: NSRange(location: index, length: 1))
            if char == "(" || char == "[" || char == "{" { depth += 1 }
            if char == ")" || char == "]" || char == "}" { depth -= 1 }
            if depth == 0 && char == op {
                return (
                    NSRange(location: range.location, length: index - range.location),
                    NSRange(location: index + 1, length: NSMaxRange(range) - index - 1)
                )
            }
        }
        return nil
    }

    private static func matchingDelimiter(_ source: NSString, open: Int) -> Int? {
        var depth = 0
        for index in open..<source.length {
            let char = source.substring(with: NSRange(location: index, length: 1))
            if char == "(" { depth += 1 }
            if char == ")" {
                depth -= 1
                if depth == 0 { return index }
            }
        }
        return nil
    }

    private static func parseFunctionCall(_ source: NSString, _ range: NSRange, name: String) -> [NSRange]? {
        guard range.length >= name.count,
              source.substring(with: NSRange(location: range.location, length: name.count)) == name else { return nil }
        let open = range.location + name.count
        guard open < NSMaxRange(range), source.character(at: open) == 40 else { return nil }
        guard let close = matchingDelimiter(source, open: open), close == NSMaxRange(range) - 1 else { return nil }
        let inner = NSRange(location: open + 1, length: max(0, close - open - 1))
        return splitArguments(source, inner)
    }

    private static func splitArguments(_ source: NSString, _ range: NSRange) -> [NSRange] {
        var result: [NSRange] = []
        var start = range.location
        var depth = 0
        guard range.length > 0 else { return [range] }
        for index in range.location..<NSMaxRange(range) {
            let char = source.substring(with: NSRange(location: index, length: 1))
            if char == "(" || char == "[" || char == "{" { depth += 1 }
            if char == ")" || char == "]" || char == "}" { depth -= 1 }
            if char == "," && depth == 0 {
                result.append(NSRange(location: start, length: index - start))
                start = index + 1
            }
        }
        let last = NSRange(location: start, length: NSMaxRange(range) - start)
        result.append(last)
        return result
    }
}
