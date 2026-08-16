import SwiftUI

struct TerminalView: View {
    @EnvironmentObject private var store: CalcoraStore
    @Environment(\.dismiss) private var dismiss
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        if store.terminalLines.isEmpty {
                            Text("Enter raw Xcas/Giac commands below.").foregroundStyle(.secondary)
                        }
                        ForEach(Array(store.terminalLines.enumerated()), id: \.offset) { _, line in
                            Text(line).font(.system(.body, design: .monospaced)).frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    .padding()
                }
                Divider()
                HStack(alignment: .bottom) {
                    TextField("x^2 + 1", text: $store.terminalInput, axis: .vertical)
                        .font(.system(.body, design: .monospaced))
                        .focused($focused)
                        .textFieldStyle(.roundedBorder)
                    Button { store.submitTerminal(); focused = true } label: { Image(systemName: "arrow.up.circle.fill").font(.title2) }
                        .disabled(store.terminalInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .padding()
            }
            .navigationTitle(LocalizedStringKey("CAS Terminal"))
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button(LocalizedStringKey("Done")) { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(LocalizedStringKey("Clear")) { store.clearTerminal() }
                }
            }
        }
    }
}

