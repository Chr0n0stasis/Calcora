import SwiftUI
import UIKit

struct HelpView: View {
    @EnvironmentObject private var store: CalcoraStore

    var body: some View {
        NavigationStack {
            List {
                if store.helpQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Section("Quick start") {
                        Text("Search by function name, alias, signature, description, related command, or example.")
                            .font(.callout).foregroundStyle(.secondary)
                    }
                }
                Section("Functions") {
                    ForEach(store.searchHelp()) { entry in
                        NavigationLink(value: entry) {
                            VStack(alignment: .leading, spacing: 3) {
                                HStack {
                                    Text(entry.name).font(.system(.body, design: .monospaced)).bold()
                                    if !entry.aliases.isEmpty { Text(entry.aliases.joined(separator: ", ")).font(.caption).foregroundStyle(.tertiary) }
                                }
                                Text(entry.syntax).font(.system(.caption, design: .monospaced)).foregroundStyle(.secondary)
                                Text(entry.description).foregroundStyle(.secondary).lineLimit(2)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Help")
            .searchable(text: $store.helpQuery, prompt: "Search functions")
            .navigationDestination(for: HelpEntry.self) { entry in HelpDetailView(entry: store.helpDetail(for: entry)) }
        }
    }
}

struct HelpDetailView: View {
    let entry: HelpEntry
    @EnvironmentObject private var store: CalcoraStore

    var body: some View {
        List {
            Section("Signature") { Text(entry.syntax).font(.system(.body, design: .monospaced)).textSelection(.enabled) }
            if !entry.aliases.isEmpty { Section("Aliases") { Text(entry.aliases.joined(separator: ", ")).font(.system(.body, design: .monospaced)) } }
            Section("Description") { Text(entry.description).textSelection(.enabled) }
            if !entry.examples.isEmpty {
                Section("Examples") {
                    ForEach(entry.examples, id: \.self) { example in
                        Button { store.evaluateAndAppend(example) } label: {
                            Label { Text(example).font(.system(.body, design: .monospaced)).frame(maxWidth: .infinity, alignment: .leading) } icon: { Image(systemName: "play.circle") }
                        }
                    }
                }
            }
            if !entry.related.isEmpty {
                Section("Related") { ForEach(entry.related, id: \.self) { Text($0).font(.system(.body, design: .monospaced)) } }
        }
        .navigationTitle(entry.name)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { UIPasteboard.general.string = entry.syntax } label: { Image(systemName: "doc.on.doc") }.accessibilityLabel("Copy signature")
                Button { store.evaluateAndAppend("help(\"\(entry.name)\")") } label: { Image(systemName: "terminal") }.accessibilityLabel("Evaluate help command")
            }
        }
    }
}
