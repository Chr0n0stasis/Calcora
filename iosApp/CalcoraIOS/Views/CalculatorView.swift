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
    @State private var showingHistory = false
    @State private var activeTab: ExtraTab = .none

    enum ExtraTab {
        case none, vars, funcs, cas
    }

    private let functionButtons = ["sin(", "cos(", "tan(", "sqrt(", "log(", "ln(", "exp(", "abs(", "floor(", "ceil("]
    private let casButtons = ["solve(", "factor(", "expand(", "diff(", "integrate(", "limit(", "sum(", "det(", "simplify(", "help("]
    private let variableButtons = ["x", "y", "z", "t", "n", "ans", "π", "e"]
    
    // Keypad layout
    private let keypadLayout: [[String]] = [
        ["AC", "(", ")", "÷"],
        ["7", "8", "9", "×"],
        ["4", "5", "6", "−"],
        ["1", "2", "3", "+"],
        ["0", ".", "⌫", "="]
    ]

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Expression and Result Area
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Spacer()
                        Picker("Mode", selection: $store.selectedMode) {
                            ForEach(EvalMode.allCases) { Text(LocalizedStringKey($0.rawValue)).tag($0) }
                        }
                        .pickerStyle(.menu)
                    }
                    .padding(.horizontal)
                    .padding(.top, 8)
                    
                    ExpressionTextView(text: $store.expression, selectedRange: $selectedRange)
                        .frame(maxHeight: .infinity)
                        .accessibilityLabel(LocalizedStringKey("Expression input"))
                    
                    if let result = store.result {
                        VStack(alignment: .trailing, spacing: 4) {
                            HStack {
                                Spacer()
                                Text(result.primary.isEmpty ? "(empty)" : result.primary)
                                    .font(.system(.title2, design: .monospaced))
                                    .foregroundStyle(result.isError ? .red : .primary)
                                    .textSelection(.enabled)
                            }
                            if let secondary = result.secondary {
                                Text(secondary).font(.system(.body, design: .monospaced)).foregroundStyle(.secondary).textSelection(.enabled)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.bottom, 12)
                        .contentShape(Rectangle())
                        .onTapGesture { showingResultDetails = true }
                    }
                }
                .background(Color(uiColor: .secondarySystemBackground))
                
                Divider()
                
                // Extra Sub-panel Toggles
                HStack(spacing: 12) {
                    extraTabButton(title: "Vars", tab: .vars)
                    extraTabButton(title: "Funcs", tab: .funcs)
                    extraTabButton(title: "CAS", tab: .cas)
                }
                .padding(.vertical, 8)
                .padding(.horizontal)
                
                // Sub-panel content
                if activeTab != .none {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            let items = activeTab == .vars ? variableButtons : (activeTab == .funcs ? functionButtons : casButtons)
                            ForEach(items, id: \.self) { item in
                                Button(item) { insert(item == "help(" ? "help(\"\")" : item) }
                                    .buttonStyle(.bordered)
                            }
                        }
                        .padding(.horizontal)
                    }
                    .padding(.bottom, 8)
                }
                
                // Keypad Area
                VStack(spacing: 8) {
                    ForEach(keypadLayout, id: \.self) { row in
                        HStack(spacing: 8) {
                            ForEach(row, id: \.self) { key in
                                keypadButton(key)
                            }
                        }
                    }
                }
                .padding()
                .background(Color(uiColor: .systemBackground))
            }
            .navigationTitle(LocalizedStringKey("Calcora"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { showingHistory = true } label: { Image(systemName: "clock.arrow.circlepath") }.accessibilityLabel(LocalizedStringKey("History"))
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { showingNaturalMath = true } label: { Image(systemName: "f.cursive") }.accessibilityLabel(LocalizedStringKey("Natural Math"))
                    Button { showingTerminal = true } label: { Image(systemName: "terminal") }.accessibilityLabel(LocalizedStringKey("CAS Terminal"))
                    Button { showingScript = true } label: { Image(systemName: "doc.text") }.accessibilityLabel(LocalizedStringKey("Script Editor"))
                }
            }
            .sheet(isPresented: $showingHistory) { HistoryView() }
            .sheet(isPresented: $showingTerminal) { TerminalView() }
            .sheet(isPresented: $showingScript) { ScriptView() }
            .sheet(isPresented: $showingNaturalMath) { NaturalMathEditorView(initialText: store.expression).environmentObject(store) }
            .sheet(isPresented: $showingResultDetails) { if let result = store.result { ResultDetailView(result: result) } }
            // If there's plot data, maybe a separate launch logic, omitted here or we use a toolbar button if result.isPlot
        }
    }
    
    private func extraTabButton(title: String, tab: ExtraTab) -> some View {
        Button {
            withAnimation { activeTab = activeTab == tab ? .none : tab }
        } label: {
            Text(LocalizedStringKey(title))
                .font(.subheadline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
        }
        .buttonStyle(.borderedProminent)
        .tint(activeTab == tab ? .accentColor : .secondary.opacity(0.2))
        .foregroundStyle(activeTab == tab ? .white : .primary)
    }

    private func keypadButton(_ key: String) -> some View {
        Button {
            handleKey(key)
        } label: {
            Text(key)
                .font(.system(.title2))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(keyColor(for: key))
    }
    
    private func keyColor(for key: String) -> Color {
        switch key {
        case "AC", "⌫": return .red
        case "=", "÷", "×", "−", "+": return .accentColor
        default: return .secondary.opacity(0.2)
        }
    }

    private func handleKey(_ key: String) {
        switch key {
        case "AC":
            store.expression = ""
            selectedRange = NSRange(location: 0, length: 0)
        case "⌫":
            backspace()
        case "=":
            store.evaluate()
        case "÷": insert("/")
        case "×": insert("*")
        case "−": insert("-")
        case "π": insert("pi")
        default:
            insert(key)
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
