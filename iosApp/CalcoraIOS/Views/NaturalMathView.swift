import Foundation
import SwiftUI

struct NaturalMathEditorView: View {
    @EnvironmentObject private var store: CalcoraStore
    @Environment(\.dismiss) private var dismiss
    @State private var text: String
    @State private var selectedRange: NSRange?

    init(initialText: String) { _text = State(initialValue: initialText) }

    private let templates = [
        ("Fraction", "(□)/(□)"), ("Power", "(□)^(□)"), ("Root", "sqrt(□)"),
        ("Integral", "integrate(□,□)"), ("Derivative", "diff(□,□)"), ("Sum", "sum(□,□,□)")
    ]

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                ExpressionTextView(text: $text, selectedRange: $selectedRange, font: .monospacedSystemFont(ofSize: 24, weight: .regular))
                    .frame(minHeight: 130, maxHeight: 230)
                    .padding(4)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(.secondary.opacity(0.25)))
                    .padding(.horizontal)
                Text("Insert structured templates at the cursor. Replace each □ placeholder before evaluating.")
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
                        ForEach(["√", "π", "∞", "≤", "≥", "≠", "∑", "∫", "→", "□"], id: \.self) { token in
                            Button(token) { insert(token) }
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
            .navigationTitle("Natural Math")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Use") { store.expression = text; dismiss() }
                        .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func insert(_ token: String) {
        let range = selectedRange ?? NSRange(location: text.utf16.count, length: 0)
        guard let swiftRange = Range(range, in: text) else { text.append(token); return }
        text.replaceSubrange(swiftRange, with: token)
        selectedRange = NSRange(location: range.location + (token as NSString).length, length: 0)
    }
}
