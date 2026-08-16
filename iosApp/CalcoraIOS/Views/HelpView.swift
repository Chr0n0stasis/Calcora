import SwiftUI
import UIKit

struct HelpView: View {
    @EnvironmentObject private var store: CalcoraStore

    var body: some View {
        NavigationStack {
            List {
                if store.helpQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Section(LocalizedStringKey("Quick start")) {
                        Text(LocalizedStringKey("Search by function name, alias, signature, description, related command, or example."))
                            .font(.callout).foregroundStyle(.secondary)
                    }
                }
                let grouped = Dictionary(grouping: store.searchHelp(), by: category(for:))
                ForEach(categoryOrder, id: \.self) { category in
                    if let entries = grouped[category], !entries.isEmpty {
                        Section {
                            ForEach(entries) { entry in
                                HStack {
                                    NavigationLink(value: entry) {
                                        VStack(alignment: .leading, spacing: 3) {
                                            HStack {
                                                Text(entry.name).font(.system(.body, design: .monospaced)).bold()
                                                if !entry.aliases.isEmpty { Text(entry.aliases.joined(separator: ", ")).font(.caption).foregroundStyle(.tertiary) }
                                            }
                                            Text(entry.syntax).font(.system(.caption, design: .monospaced)).foregroundStyle(.secondary)
                                            Text(entry.description).foregroundStyle(.secondary).lineLimit(2)
                                            if let example = entry.examples.first {
                                                Button {
                                                    fillCalculator(with: example)
                                                } label: {
                                                    Label(example, systemImage: "play.circle")
                                                        .font(.caption)
                                                        .frame(maxWidth: .infinity, alignment: .leading)
                                                }
                                                .buttonStyle(.borderless)
                                            }
                                        }
                                    }
                                    Button {
                                        fillCalculator(with: entry.syntax)
                                    } label: {
                                        Image(systemName: "plus.circle.fill")
                                    }
                                    .buttonStyle(.borderless)
                                    .accessibilityLabel("Insert \(entry.name)")
                                }
                            }
                        } header: {
                            Label(LocalizedStringKey(category), systemImage: categorySymbol(category))
                        }
                    }
                }
            }
            .navigationTitle(LocalizedStringKey("Help"))
            .searchable(text: $store.helpQuery, prompt: Text("Search functions"))
            .navigationDestination(for: HelpEntry.self) { entry in HelpDetailView(entry: store.helpDetail(for: entry)) }
        }
    }

    private let categoryOrder = ["Trigonometry", "Calculus", "Algebra", "Linear Algebra", "Plotting", "Number Theory", "Programming", "Other"]

    private func categorySymbol(_ category: String) -> String {
        switch category {
        case "Trigonometry": return "angle"
        case "Calculus": return "function"
        case "Algebra": return "x.squareroot"
        case "Linear Algebra": return "square.grid.3x3"
        case "Plotting": return "chart.xyaxis.line"
        case "Number Theory": return "number"
        case "Programming": return "chevron.left.forwardslash.chevron.right"
        default: return "ellipsis.circle"
        }
    }

    private func category(for entry: HelpEntry) -> String {
        let name = entry.name.lowercased()
        let aliases = entry.aliases.map { $0.lowercased() }
        if ["sin", "cos", "tan", "asin", "acos", "atan"].contains(name) || !Set(["sin", "cos", "tan", "asin", "acos", "atan"]).intersection(aliases).isEmpty {
            return "Trigonometry"
        }
        if ["diff", "integrate", "int", "limit", "sum", "taylor", "series"].contains(name) || !Set(["diff", "integrate", "int", "limit", "sum", "taylor", "series"]).intersection(aliases).isEmpty {
            return "Calculus"
        }
        if ["solve", "factor", "expand", "simplify", "normal", "subst", "partfrac"].contains(name) || !Set(["solve", "factor", "expand", "simplify", "normal", "subst", "partfrac"]).intersection(aliases).isEmpty {
            return "Algebra"
        }
        if ["det", "inv", "transpose", "rank", "rref", "eigenvals"].contains(name) || !Set(["det", "inv", "transpose", "rank", "rref", "eigenvals"]).intersection(aliases).isEmpty {
            return "Linear Algebra"
        }
        if ["plot", "plot3d", "plotparam", "plotlist", "plotseq"].contains(name) || !Set(["plot", "plot3d", "plotparam", "plotlist", "plotseq"]).intersection(aliases).isEmpty {
            return "Plotting"
        }
        if ["gcd", "lcm", "ifactor", "isprime", "nextprime"].contains(name) || !Set(["gcd", "lcm", "ifactor", "isprime", "nextprime"]).intersection(aliases).isEmpty {
            return "Number Theory"
        }
        if ["fft", "ifft", "makelist", "makemat", "map"].contains(name) || !Set(["fft", "ifft", "makelist", "makemat", "map"]).intersection(aliases).isEmpty {
            return "Programming"
        }
        return "Other"
    }

    private func fillCalculator(with expression: String) {
        store.expression = expression
        store.selectedTab = .calculator
    }
}

struct HelpDetailView: View {
    let entry: HelpEntry
    @EnvironmentObject private var store: CalcoraStore

    var body: some View {
        List {
            Section(LocalizedStringKey("Signature")) { Text(entry.syntax).font(.system(.body, design: .monospaced)).textSelection(.enabled) }
            if !entry.aliases.isEmpty { Section(LocalizedStringKey("Aliases")) { Text(entry.aliases.joined(separator: ", ")).font(.system(.body, design: .monospaced)) } }
            Section(LocalizedStringKey("Description")) { Text(entry.description).textSelection(.enabled) }
            if !entry.examples.isEmpty {
                Section(LocalizedStringKey("Examples")) {
                    ForEach(entry.examples, id: \.self) { example in
                        Button { fillCalculator(with: example) } label: {
                            Label { Text(example).font(.system(.body, design: .monospaced)).frame(maxWidth: .infinity, alignment: .leading) } icon: { Image(systemName: "play.circle") }
                        }
                    }
                }
            }
            if !entry.related.isEmpty {
                Section(LocalizedStringKey("Related")) { ForEach(entry.related, id: \.self) { Text($0).font(.system(.body, design: .monospaced)) } }
            }
        }
        .navigationTitle(entry.name)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { UIPasteboard.general.string = entry.syntax } label: { Image(systemName: "doc.on.doc") }.accessibilityLabel(LocalizedStringKey("Copy signature"))
                Button {
                    store.expression = entry.syntax
                    store.selectedTab = .calculator
                } label: { Image(systemName: "plus.circle.fill") }.accessibilityLabel("Insert into calculator")
            }
        }
    }

    private func fillCalculator(with expression: String) {
        store.expression = expression
        store.selectedTab = .calculator
    }
}
