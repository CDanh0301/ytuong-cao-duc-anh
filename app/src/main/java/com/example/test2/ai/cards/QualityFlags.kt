package com.example.test2.ai.cards

/**
 * Feature flags for progressive rollout and zero-risk rollback of Action Card enhancements (Plan v4).
 */
object QualityFlags {
    /**
     * Controls whether PhoneExtractor and ContactNamer V2 are active.
     * When false, falls back to legacy contact extraction.
     */
    var NEW_CONTACT_EXTRACTOR: Boolean = true

    /**
     * Controls whether AddressExtractor V2 and place name heuristics without photoTitle fallback are active.
     * When false, falls back to legacy address extraction.
     */
    var NEW_ADDRESS_EXTRACTOR: Boolean = true

    /**
     * Controls whether ImageDecoder with single-pass memory-safe decoding and shared bitmap is active.
     */
    var SHARED_DECODE: Boolean = true

    /**
     * Controls whether PhotoProcessingCoordinator extracts and saves action cards immediately on import/capture.
     */
    var INSTANT_EXTRACTION: Boolean = true

    /**
     * Controls whether UI observes action cards reactively via Flow.
     */
    var REACTIVE_CARDS_UI: Boolean = true

    /**
     * Controls whether opening an image with legacy extractor version triggers clean background re-extraction.
     */
    var REEXTRACT_ON_OPEN: Boolean = true

    /**
     * Controls fail-closed privacy gate: blocks identity docs, OTPs, and credit card numbers from cloud API.
     */
    var CLOUD_PRIVACY_GATE: Boolean = true

    /**
     * Optional extended PII scrubbing (masks names/addresses before cloud transmission). Default false.
     */
    var PII_SCRUBBER_PLUS: Boolean = false

    /**
     * Controls WorkManager hardening (foreground info, batch limits, cancellation checks).
     */
    var WORKER_HARDENING: Boolean = true

    /**
     * Max pixels to decode. Defaults to 4.0 MP, or 2.5 MP on low-RAM devices.
     */
    var maxDecodePixels: Int = 4_000_000
}
