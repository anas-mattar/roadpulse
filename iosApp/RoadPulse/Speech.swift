import AVFoundation
import Foundation

/// Speaks the turn instructions. Music from other apps is lowered while a prompt plays.
final class Speech: NSObject, AVSpeechSynthesizerDelegate {
    private let synthesizer = AVSpeechSynthesizer()
    var muted = false

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    /// True when the phone has a voice for this language tag ("ms-MY", "ar", "en-US").
    static func hasVoice(for tag: String) -> Bool { voice(for: tag) != nil }

    private static func voice(for tag: String) -> AVSpeechSynthesisVoice? {
        if let exact = AVSpeechSynthesisVoice(language: tag) { return exact }
        let prefix = String(tag.prefix(2))
        return AVSpeechSynthesisVoice.speechVoices().first { $0.language.hasPrefix(prefix) }
    }

    func speak(_ text: String, languageTag: String) {
        guard !muted else { return }
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .voicePrompt, options: [.duckOthers])
        try? session.setActive(true)
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = Speech.voice(for: languageTag)
        synthesizer.speak(utterance)
    }

    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        // Let the music come back up when nothing else is queued.
        if !synthesizer.isSpeaking {
            try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
        }
    }
}
