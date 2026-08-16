import SwiftUI

struct HistoryView: View {
    @EnvironmentObject private var store: CalcoraStore

    var body: some View {
        NavigationStack {
            List {
                if store.history.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "clock.arrow.circlepath").font(.largeTitle).foregroundStyle(.secondary)
                        Text(LocalizedStringKey("No History")).font(.headline)
                        Text(LocalizedStringKey("Evaluate an expression to create a history entry.")).font(.subheadline).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 48)
                } else {
                    ForEach(store.history) { entry in
                        Button { store.restore(entry) } label: {
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(entry.expression).font(.system(.body, design: .monospaced)).bold()
                                    Spacer()
                                    if entry.isPlot { Image(systemName: "chart.xyaxis.line") }
                                }
                                Text(entry.result).font(.system(.caption, design: .monospaced)).foregroundStyle(.secondary).lineLimit(3)
                                Text(entry.timestamp, style: .relative).font(.caption2).foregroundStyle(.tertiary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.plain)
                    }
                    .onDelete(perform: store.deleteHistory)
                }
            }
            .navigationTitle(LocalizedStringKey("History"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(LocalizedStringKey("Clear"), role: .destructive) { store.clearHistory() }
                        .disabled(store.history.isEmpty)
                }
            }
        }
    }
}

