import SwiftUI
import UIKit

struct CalculatorView: View {
    @EnvironmentObject private var store: CalcoraStore
    @State private var selectedRange: NSRange?
    @State private var showingTerminal = false
    @State private var showingScript = false
    @State private var showingPlot = false
    @State private var showingNaturalMath = false
    @State private var showingResultDetails = false
    @State private var showFunctions = true
    @State private var showCAS = false
    @State private var showVariables = false

    private let numberButtons = [
        ["7", "8", "9", "÷"], ["4", "5", "6", "×"], ["1", "2", "3", "−"], ["0", ".", "(", ")"],
        ["+", "−", "*", "/"], ["^", "%", ",", "pi"]
    ]
    private let functionButtons = ["sin(", "cos(", "tan(", "sqrt(", "log(", "ln(", "exp(", "abs(", "floor(", "ceil(")]
    private let casButtons = ["solve(", "factor(", "expand(", "diff(", "integrate(", "limit(", "sum(", "det(", "simplify(", "help("]
    private let variableButtons = ["x", "y", "z", "t", "n", "ans", "pi", "e"]

    var body: some View {
        NavigationStack {
            GeometryReader { proxy in
                Group {
                    if proxy.size.width >= 700 {
                        HStack(alignment: .top, spacing: 16) {
                            ScrollView { leftColumn.padding() }
                                .frame(maxWidth: .infinity)
                            ScrollView { keypadColumn.padding() }
                                .frame(width: min(420, proxy.size.width * 0.42))
                        }
                    } else {
                        ScrollView { phoneColumn.padding() }
                    }
                }
                .scrollDismissesKeyboard(.interactively)
            }
            .navigationTitle("Calcora")
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { showingNaturalMath = true } label: { Image(systemName: "function") }.accessibilityLabel("Natural Math")
                    Button { showingTerminal = true } label: { Image(systemName: "terminal") }.accessibilityLabel("CAS Terminal")
                    Button { showingScript = true } label: { Image(systemName: "doc.text") }.accessibilityLabel("Script Editor")
                }
            }
            .sheet(isPresented: $showingTerminal) { TerminalView() }
            .sheet(isPresented: $showingScript) { ScriptView() }
            .sheet(isPresented: $showingNaturalMath) { NaturalMathEditorView(initialText: store.expression).environmentObject(store) }
            .sheet(isPresented: $showingResultDetails) { if let result = store.result { ResultDetailView(result: result) } }
            .sheet(isPresented: $showingPlot) { PlotView(items: store.plotItems()) }
        }
    }

    private var phoneColumn: some View {
        VStack(alignment: .leading, spacing: 16) { inputCard; suggestionRow; keypad; actionRow; resultSection; recentSection }
    }

    private var leftColumn: some View {
        VStack(alignment: .leading, spacing: 16) { inputCard; suggestionRow; resultSection; recentSection }
    }

    private var keypadColumn: some View {
        VStack(alignment: .leading, spacing: 12) { keypad; actionRow }
    }

    private var inputCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Expression").font(.headline)
                Spacer()
                Picker("Mode", selection: $store.selectedMode) {
                    ForEach(EvalMode.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.menu)
            }
            ExpressionTextView(text: $store.expression, selectedRange: $selectedRange)
                .frame(minHeight: 86, maxHeight: 160)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(.secondary.opacity(0.25)))
                .accessibilityLabel("Expression input")
            HStack(spacing: 8) {
                Button("AC", systemImage: "xmark.circle") { store.expression = ""; selectedRange = NSRange(location: 0, length: 0) }
                    .buttonStyle(.bordered)
                Button { backspace() } label: { Image(systemName: "delete.left") }.buttonStyle(.bordered).accessibilityLabel("Backspace")
                Spacer()
                Button { store.evaluate() } label: {
                    Label(store.isEvaluating ? "Evaluating…" : "EXE", systemImage: "equal.square.fill")
                }
                .buttonStyle(.borderedProminent)
                .disabled(store.isEvaluating)
            }
        }
        .padding()
        .background(.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder private var suggestionRow: some View {
        if !store.autocompleteSuggestions.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(store.autocompleteSuggestions, id: \.self) { suggestion in
                        Button(suggestion) { insert(suggestion + "(") }
                            .buttonStyle(.bordered)
                            .font(.system(.caption, design: .monospaced))
                    }
                }
            }
        }
    }

    private var keypad: some View {
        VStack(alignment: .leading, spacing: 10) {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                ForEach(numberButtons.flatMap { $0 }, id: \.self) { token in keyButton(token) }
            }
            DisclosureGroup("Variables", isExpanded: $showVariables) {
                tokenGrid(variableButtons)
            }
            DisclosureGroup("Mathematical functions", isExpanded: $showFunctions) {
                tokenGrid(functionButtons)
            }
            DisclosureGroup("CAS functions", isExpanded: $showCAS) {
                tokenGrid(casButtons)
            }
        }
    }

    private func tokenGrid(_ tokens: [String]) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 74), spacing: 8)], spacing: 8) {
            ForEach(tokens, id: \.self) { token in keyButton(token) }
        }
        .padding(.top, 6)
    }

    private func keyButton(_ token: String) -> some View {
        Button { insert(token == "help(" ? "help(\"\")" : token) } label: {
            Text(token).font(.system(.body, design: .monospaced)).frame(maxWidth: .infinity, minHeight: 42)
        }
        .buttonStyle(.bordered)
        .accessibilityLabel("Insert \(token)")
    }

    private var actionRow: some View {
        HStack(spacing: 8) {
            Button { showingTerminal = true } label: { Label("CAS Terminal", systemImage: "terminal") }
            Button { showingScript = true } label: { Label("Script", systemImage: "doc.text") }
        }
        .buttonStyle(.bordered)
    }

    @ViewBuilder private var resultSection: some View {
        if let result = store.result {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Label(result.isError ? "Error" : "Result", systemImage: result.isError ? "exclamationmark.triangle" : "checkmark.circle")
                        .font(.headline)
                        .foregroundStyle(result.isError ? .red : .primary)
                    Spacer()
                    Button { showingResultDetails = true } label: { Image(systemName: "info.circle") }.accessibilityLabel("Result details")
                    Button { UIPasteboard.general.string = result.primary } label: { Image(systemName: "doc.on.doc") }.accessibilityLabel("Copy result")
                }
                Text(result.primary.isEmpty ? "(empty)" : result.primary)
                    .font(.system(.title3, design: .monospaced)).textSelection(.enabled)
                if let secondary = result.secondary {
                    Text(secondary).font(.system(.body, design: .monospaced)).foregroundStyle(.secondary).textSelection(.enabled)
                }
                if result.isPlot {
                    Button { showingPlot = true } label: { Label("View plot", systemImage: "chart.xyaxis.line") }.buttonStyle(.borderedProminent)
                }
                Text("Backend: \(result.backend) · Mode: \(result.mode.rawValue)").font(.caption).foregroundStyle(.secondary)
            }
            .padding()
            .background(result.isError ? .red.opacity(0.1) : .accentColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 16))
        }
    }

    private var recentSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack { Text("Recent calculations").font(.headline); Spacer(); Button("See all") { store.selectedTab = .history }.font(.subheadline) }
            if store.history.isEmpty {
                Text("Your calculation history will appear here.").foregroundStyle(.secondary)
            } else {
                ForEach(store.history.prefix(3)) { entry in
                    Button { store.restore(entry) } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(entry.expression).font(.system(.body, design: .monospaced)).lineLimit(1)
                            Text(entry.result).font(.system(.caption, design: .monospaced)).foregroundStyle(.secondary).lineLimit(2)
                        }.frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    Divider()
                }
            }
        }
    }

    private func insert(_ token: String) {
        let range = selectedRange ?? NSRange(location: (store.expression as NSString).length, length: 0)
        guard let stringRange = Range(range, in: store.expression) else { store.expression += token; return }
        store.expression.replaceSubrange(stringRange, with: token)
        selectedRange = NSRange(location: range.location + (token as NSString).length, length: 0)
    }

    private func backspace() {
        let length = (store.expression as NSString).length
        var range = selectedRange ?? NSRange(location: length, length: 0)
        if range.length == 0 && range.location > 0 { range.location -= 1; range.length = 1 }
        guard range.length > 0, let stringRange = Range(range, in: store.expression) else { return }
        store.expression.removeSubrange(stringRange)
        selectedRange = NSRange(location: range.location, length: 0)
    }
}

