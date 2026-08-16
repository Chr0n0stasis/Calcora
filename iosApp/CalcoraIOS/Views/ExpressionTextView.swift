import SwiftUI
import UIKit

struct ExpressionTextView: UIViewRepresentable {
    @Binding var text: String
    @Binding var selectedRange: NSRange?
    var font: UIFont = .monospacedSystemFont(ofSize: 20, weight: .regular)
    var isEditable: Bool = true
    var onCommit: (() -> Void)? = nil

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UITextView {
        let view = UITextView()
        view.delegate = context.coordinator
        view.font = font
        view.backgroundColor = .clear
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
        let toolbar = UIToolbar(frame: CGRect(x: 0, y: 0, width: 0, height: 44))
        toolbar.items = [
            UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil),
            UIBarButtonItem(barButtonSystemItem: .done, target: view, action: #selector(UIResponder.resignFirstResponder))
        ]
        view.inputAccessoryView = toolbar
        view.text = text
        return view
    }

    func updateUIView(_ view: UITextView, context: Context) {
        if view.text != text { view.text = text }
        if let selectedRange, view.selectedRange != selectedRange { view.selectedRange = selectedRange }
        view.isEditable = isEditable
        view.font = font
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: ExpressionTextView
        init(_ parent: ExpressionTextView) { self.parent = parent }
        func textViewDidChange(_ textView: UITextView) { parent.text = textView.text }
        func textViewDidChangeSelection(_ textView: UITextView) { parent.selectedRange = textView.selectedRange }
        func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText replacement: String) -> Bool {
            if replacement == "\n" { parent.onCommit?(); return false }
            return true
        }
    }
}
