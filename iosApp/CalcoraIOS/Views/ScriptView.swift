import SwiftUI
import UniformTypeIdentifiers

struct ScriptTextDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.plainText, .text, UTType(exportedAs: "com.example.calcora.xcas")] }
    var text: String
    init(text: String = "") { self.text = text }
    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents, let value = String(data: data, encoding: .utf8) else { throw CocoaError(.fileReadCorruptFile) }
        text = value
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: Data(text.utf8)) }
}

struct ScriptView: View {
    @EnvironmentObject private var store: CalcoraStore
    @Environment(\.dismiss) private var dismiss
    @State private var saveName = "Untitled"
    @State private var showingSave = false
    @State private var showingImporter = false
    @State private var showingExporter = false
    @State private var exportDocument = ScriptTextDocument()
    @State private var errorMessage = ""

    var body: some View {
        NavigationStack {
            GeometryReader { proxy in
                Group {
                    if proxy.size.width >= 700 {
                        HStack(spacing: 0) { scriptList.frame(width: 230); Divider(); editor.padding() }
                    } else {
                        VStack(spacing: 12) { editor.padding(.horizontal); scriptList.frame(maxHeight: 190) }
                    }
                }
            }
            .navigationTitle(LocalizedStringKey("Script Editor"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(LocalizedStringKey("Done")) { dismiss() } }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { saveName = store.scriptName.replacingOccurrences(of: ".xcas", with: ""); showingSave = true } label: { Image(systemName: "square.and.arrow.down") }.accessibilityLabel(LocalizedStringKey("Save script"))
                    Button { exportDocument = ScriptTextDocument(text: store.scriptText); showingExporter = true } label: { Image(systemName: "square.and.arrow.up") }.accessibilityLabel(LocalizedStringKey("Export script"))
                    Button { showingImporter = true } label: { Image(systemName: "folder") }.accessibilityLabel(LocalizedStringKey("Import script"))
                }
            }
            .alert(LocalizedStringKey("Save Script"), isPresented: $showingSave) {
                TextField(LocalizedStringKey("Script name"), text: $saveName)
                Button(LocalizedStringKey("Save")) { doSave() }
                Button(LocalizedStringKey("Cancel"), role: .cancel) {}
            } message: { Text("Scripts are stored in the app's private Documents support area.") }
            .alert(LocalizedStringKey("Script Error"), isPresented: Binding(get: { !errorMessage.isEmpty }, set: { if !$0 { errorMessage = "" } })) { Button(LocalizedStringKey("OK")) {} } message: { Text(errorMessage) }
            .fileImporter(isPresented: $showingImporter, allowedContentTypes: [.plainText, .text, UTType(exportedAs: "com.example.calcora.xcas")], allowsMultipleSelection: false) { result in
                if case .success(let urls) = result, let url = urls.first { do { try store.importScript(from: url) } catch { errorMessage = error.localizedDescription } }
            }
            .fileExporter(isPresented: $showingExporter, document: exportDocument, contentType: .plainText, defaultFilename: store.scriptName) { result in
                if case .failure(let error) = result { errorMessage = error.localizedDescription }
            }
        }
    }

    private var editor: some View {
        VStack(spacing: 12) {
            ExpressionTextView(text: $store.scriptText, selectedRange: .constant(nil), font: .monospacedSystemFont(ofSize: 16, weight: .regular))
                .frame(minHeight: 220)
                .padding(8)
                .background(.secondary.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
            HStack {
                Button { store.runScript() } label: { Label(LocalizedStringKey("Run"), systemImage: "play.fill") }.buttonStyle(.borderedProminent)
                Button(LocalizedStringKey("Clear output")) { store.clearScriptOutput() }.buttonStyle(.bordered)
                Spacer()
                Text(store.scriptName).font(.caption).foregroundStyle(.secondary)
            }
            ScrollView {
                Text(store.scriptOutput.isEmpty ? "Output will appear here." : store.scriptOutput.joined(separator: "\n"))
                    .font(.system(.caption, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .textSelection(.enabled)
            }
            .frame(maxHeight: 220)
            .padding()
            .background(.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
        }
    }

    private var scriptList: some View {
        List {
            Section(LocalizedStringKey("Saved scripts")) {
                if store.scriptNames.isEmpty { Text(LocalizedStringKey("No saved scripts")).foregroundStyle(.secondary) }
                ForEach(store.scriptNames, id: \.self) { name in
                    HStack {
                        Button(name) { doLoad(name) }.frame(maxWidth: .infinity, alignment: .leading)
                        Button(role: .destructive) { doDelete(name) } label: { Image(systemName: "trash") }.buttonStyle(.borderless)
                    }
                }
            }
        }
        .listStyle(.sidebar)
    }

    private func doSave() {
        do { try store.saveScript(named: saveName) } catch { errorMessage = error.localizedDescription }
    }
    private func doLoad(_ name: String) {
        do { try store.loadScript(named: name) } catch { errorMessage = error.localizedDescription }
    }
    private func doDelete(_ name: String) {
        do { try store.deleteScript(named: name); store.objectWillChange.send() } catch { errorMessage = error.localizedDescription }
    }
}



