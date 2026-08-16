import SwiftUI
import UIKit

struct ExpressionTextView: UIViewRepresentable {
    @Binding var text: String
    @Binding var selectedRange: NSRange?
    var font: UIFont = .monospacedSystemFont(ofSize: 20, weight: .regular)
    var isEditable: Bool = true
    var onCommit: (() -> Void)? = nil
    var placeholderColor: UIColor = .secondaryLabel

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UITextView {
        let view = UITextView()
        view.delegate = context.coordinator
        view.font = font
        view.backgroundColor = .clear
        view.textColor = .label
        view.isEditable = isEditable
        view.isScrollEnabled = true
        view.alwaysBounceVertical = false
        view.textContainerInset = UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)
        view.autocorrectionType = .no
        view.autocapitalizationType = .none
        view.smartQuotesType = .no
        view.smartDashesType = .no
        view.smartInsertDeleteType = .no
        view.returnKeyType = .done
        context.coordinator.textView = view
        let leftSwipe = UISwipeGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.moveCaretLeft))
        leftSwipe.direction = .left
        view.addGestureRecognizer(leftSwipe)
        let rightSwipe = UISwipeGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.moveCaretRight))
        rightSwipe.direction = .right
        view.addGestureRecognizer(rightSwipe)
        let toolbar = UIToolbar(frame: CGRect(x: 0, y: 0, width: 0, height: 44))
        toolbar.items = [
            UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil),
            UIBarButtonItem(barButtonSystemItem: .done, target: view, action: #selector(UIResponder.resignFirstResponder))
        ]
        view.inputAccessoryView = toolbar
        view.attributedText = Self.makeAttributedText(text, placeholderColor: placeholderColor)
        return view
    }

    func updateUIView(_ view: UITextView, context: Context) {
        if view.text != text {
            view.attributedText = Self.makeAttributedText(text, placeholderColor: placeholderColor)
        }
        if let selectedRange, view.selectedRange != selectedRange { view.selectedRange = selectedRange }
        view.isEditable = isEditable
        view.font = font
    }

    private static func makeAttributedText(_ text: String, placeholderColor: UIColor) -> NSAttributedString {
        let attributed = NSMutableAttributedString(string: text)
        var searchRange = NSRange(location: 0, length: attributed.length)
        while searchRange.length > 0 {
            let found = (text as NSString).range(of: "□", options: [], range: searchRange)
            guard found.location != NSNotFound else { break }
            attributed.addAttribute(.foregroundColor, value: placeholderColor, range: found)
            searchRange = NSRange(location: found.location + found.length, length: attributed.length - found.location - found.length)
        }
        return attributed
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: ExpressionTextView
        weak var textView: UITextView?
        init(_ parent: ExpressionTextView) { self.parent = parent }
        func textViewDidChange(_ textView: UITextView) { parent.text = textView.text }
        func textViewDidChangeSelection(_ textView: UITextView) { parent.selectedRange = textView.selectedRange }
        func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText replacement: String) -> Bool {
            if replacement == "\n" { parent.onCommit?(); return false }
            return true
        }
        @objc func moveCaretLeft() {
            guard let textView, textView.isFirstResponder else { return }
            let current = textView.selectedRange
            if current.length > 0 {
                textView.selectedRange = NSRange(location: current.location, length: 0)
            } else if current.location > 0 {
                textView.selectedRange = NSRange(location: current.location - 1, length: 0)
            }
        }
        @objc func moveCaretRight() {
            guard let textView, textView.isFirstResponder else { return }
            let current = textView.selectedRange
            if current.length > 0 {
                textView.selectedRange = NSRange(location: NSMaxRange(current), length: 0)
            } else if current.location < (textView.text ?? "").utf16.count {
                textView.selectedRange = NSRange(location: current.location + 1, length: 0)
            }
        }
    }
}
