import SwiftUI
import UIKit

struct ResultDetailView: View {
    let result: CalcResult
    @Environment(\.dismiss) private var dismiss
    @State private var copiedLabel = ""

    var body: some View {
        NavigationStack {
            List {
                Section("Input") { ValueRow(label: "Expression", value: result.input) }
                if result.isError {
                    Section("Error") { ValueRow(label: "Message", value: result.error ?? "Unknown error", copyable: true, copiedLabel: $copiedLabel) }
                } else {
                    Section("Symbolic") { ValueRow(label: "Result", value: result.symbolic.isEmpty ? result.primary : result.symbolic, copyable: true, copiedLabel: $copiedLabel) }
                    if !result.numeric.isEmpty { Section("Numeric") { ValueRow(label: "Approximation", value: result.numeric, copyable: true, copiedLabel: $copiedLabel) } }
                    if !result.latex.isEmpty { Section("LaTeX") { ValueRow(label: "Exact", value: result.latex, copyable: true, copiedLabel: $copiedLabel) } }
                    if !result.numericLatex.isEmpty { Section("Numeric LaTeX") { ValueRow(label: "Approximation", value: result.numericLatex, copyable: true, copiedLabel: $copiedLabel) } }
                }
                Section("Metadata") {
                    LabeledContent("Backend", value: result.backend)
                    LabeledContent("Mode", value: result.mode.rawValue)
                    if result.isPlot { LabeledContent("Graphic", value: "Yes") }
                }
            }
            .navigationTitle("Result Details")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .overlay(alignment: .bottom) {
                if !copiedLabel.isEmpty { Text("Copied \(copiedLabel)").font(.caption).padding(8).background(.thinMaterial, in: Capsule()).padding() }
            }
        }
    }
}

private struct ValueRow: View {
    let label: String
    let value: String
    var copyable = false
    @Binding var copiedLabel: String

    init(label: String, value: String, copyable: Bool = false, copiedLabel: Binding<String> = .constant("")) {
        self.label = label; self.value = value; self.copyable = copyable; _copiedLabel = copiedLabel
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(label).font(.caption).foregroundStyle(.secondary)
                Spacer()
                if copyable {
                    Button { UIPasteboard.general.string = value; copiedLabel = label } label: { Image(systemName: "doc.on.doc") }
                        .accessibilityLabel("Copy \(label)")
                }
            }
            Text(value.isEmpty ? "—" : value)
                .font(.system(.body, design: .monospaced))
                .textSelection(.enabled)
        }
    }
}
