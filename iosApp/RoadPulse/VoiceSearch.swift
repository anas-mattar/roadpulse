import AVFoundation
import Foundation
import Speech

/// "Search by voice": listens for a few seconds and returns what was said (Apple's speech
/// recognition; asks for the microphone and speech recognition the first time).
final class VoiceSearch: ObservableObject {
    @Published var listening = false
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    /// [onText] gets the words so far and, last, the final text (true); empty text when it failed.
    func start(localeTag: String, onText: @escaping (String, Bool) -> Void) {
        guard !listening else { return }
        SFSpeechRecognizer.requestAuthorization { status in
            guard status == .authorized else {
                DispatchQueue.main.async { onText("", true) }
                return
            }
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                DispatchQueue.main.async {
                    if granted { self.begin(localeTag: localeTag, onText: onText) } else { onText("", true) }
                }
            }
        }
    }

    private func begin(localeTag: String, onText: @escaping (String, Bool) -> Void) {
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: localeTag)), recognizer.isAvailable else {
            onText("", true)
            return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: [.duckOthers])
            try session.setActive(true, options: [.notifyOthersOnDeactivation])
            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            self.request = request
            let input = engine.inputNode
            input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buffer, _ in
                request.append(buffer)
            }
            engine.prepare()
            try engine.start()
            listening = true
            task = recognizer.recognitionTask(with: request) { result, error in
                let text = result?.bestTranscription.formattedString ?? ""
                let final = (result?.isFinal ?? false) || error != nil
                DispatchQueue.main.async {
                    guard self.listening else { return }
                    onText(text, final)
                    if final { self.stop() }
                }
            }
            // Stop listening after a few seconds; the last words then come back as final.
            DispatchQueue.main.asyncAfter(deadline: .now() + 6) {
                if self.listening { self.request?.endAudio() }
            }
        } catch {
            stop()
            onText("", true)
        }
    }

    func stop() {
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        task?.cancel()
        request = nil
        task = nil
        listening = false
        try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
    }

    /// Recognition language for the app language.
    static func localeTag(for languageCode: String) -> String {
        switch languageCode {
        case "ms": return "ms-MY"
        case "ar": return "ar-SA"
        default: return "en-US"
        }
    }
}
