import { describe, expect, it, afterEach } from "vitest";
import {
  Base64Url,
  WebAuthn,
  authenticationCredential,
  creationOptionsFromJson,
  credentialToJson,
  publicKeyCredentialCreationOptions,
  publicKeyCredentialParameters,
  requestOptionsFromJson,
  registrationCredential,
} from "../src/index.js";

const originalPublicKeyCredential = globalThis.PublicKeyCredential;

afterEach(() => {
  if (originalPublicKeyCredential === undefined) {
    Reflect.deleteProperty(globalThis, "PublicKeyCredential");
  } else {
    Object.defineProperty(globalThis, "PublicKeyCredential", {
      configurable: true,
      value: originalPublicKeyCredential,
    });
  }
});

function withoutNativeParser(): void {
  Object.defineProperty(globalThis, "PublicKeyCredential", {
    configurable: true,
    value: function PublicKeyCredential() {},
  });
}

function bytes(...values: number[]): ArrayBuffer {
  return new Uint8Array(values).buffer;
}

describe("Base64Url", () => {
  it("round-trips arbitrary bytes without padding", () => {
    const value = bytes(0, 1, 2, 127, 128, 250, 251, 252, 255);
    expect(Base64Url.encode(value)).toBe("AAECf4D6-_z_");
    expect([...Base64Url.decodeToBytes(Base64Url.encode(value))]).toEqual([
      0, 1, 2, 127, 128, 250, 251, 252, 255,
    ]);
  });

  it("accepts canonical padded input and rejects malformed input", () => {
    expect([...Base64Url.decodeToBytes("Zg==")]).toEqual([102]);
    for (const value of ["a", "a+b/", "Zg=", "Z===", "Zm 8", "💥"]) {
      expect(() => Base64Url.decode(value)).toThrow("Invalid base64url");
    }
  });
});

describe("WebAuthnCodecs", () => {
  it("decodes registration and authentication JSON with the Level 2 fallback", () => {
    withoutNativeParser();
    const creation = creationOptionsFromJson({
      rp: { id: "example.com", name: "Example" },
      user: { id: "dXNlcjEyMw", name: "ada@example.com", displayName: "Ada" },
      challenge: "Y2hhbGxlbmdlLTEyMw",
      pubKeyCredParams: [{ type: "public-key", alg: -7 }],
      authenticatorSelection: { residentKey: "preferred", requireResidentKey: false },
    });
    expect(Base64Url.encode(creation.challenge)).toBe("Y2hhbGxlbmdlLTEyMw");
    expect(creation.authenticatorSelection?.requireResidentKey).toBe(false);

    const request = requestOptionsFromJson(
      JSON.stringify({
        challenge: "YXV0aC1jaGFsbGVuZ2U",
        rpId: "example.com",
        allowCredentials: [{ id: "Y3JlZC0y", type: "public-key" }],
      }),
    );
    expect(Base64Url.encode(request.challenge)).toBe("YXV0aC1jaGFsbGVuZ2U");
    expect(request.allowCredentials?.[0]?.id).toBeInstanceOf(ArrayBuffer);
  });

  it("rejects missing fields, wrong types, and invalid base64url", () => {
    withoutNativeParser();
    expect(() => requestOptionsFromJson({ rpId: "example.com" })).toThrow("challenge");
    expect(() => requestOptionsFromJson({ challenge: "YQ", allowCredentials: "no" })).toThrow(
      "allowCredentials",
    );
    expect(() => requestOptionsFromJson({ challenge: "not valid" })).toThrow("base64url");
  });

  it("serializes fallback credentials and recursively encodes extension buffers", () => {
    withoutNativeParser();
    const credential = {
      id: "credential-id",
      rawId: bytes(1, 2, 3),
      type: "public-key",
      authenticatorAttachment: null,
      response: {
        clientDataJSON: bytes(4, 5),
        attestationObject: bytes(6, 7),
        getTransports: () => ["internal"],
        getAuthenticatorData: () => bytes(8, 9),
        getPublicKey: () => bytes(10, 11),
        getPublicKeyAlgorithm: () => -7,
      },
      getClientExtensionResults: () => ({
        prf: { results: { first: bytes(12, 13) } },
      }),
    } as unknown as PublicKeyCredential;

    const payload = registrationCredential(credential);
    expect(payload.rawId).toBe("AQID");
    expect(payload.response).toMatchObject({
      clientDataJSON: "BAU",
      attestationObject: "Bgc",
      authenticatorData: "CAk",
      publicKey: "Cgs",
      publicKeyAlgorithm: -7,
      transports: ["internal"],
    });
    expect(payload.clientExtensionResults.prf).toEqual({ results: { first: "DA0" } });
  });

  it("uses native credential JSON with its receiver intact", () => {
    const credential = {
      id: "native-id",
      toJSON(this: { id: string }) {
        if (this.id !== "native-id") throw new Error("receiver lost");
        return { source: "native" };
      },
    } as unknown as PublicKeyCredential;
    expect(credentialToJson(credential)).toEqual({ source: "native" });
  });

  it("serializes authentication responses with an optional user handle", () => {
    const credential = {
      id: "credential-id",
      rawId: bytes(1),
      type: "public-key",
      authenticatorAttachment: "cross-platform",
      response: {
        clientDataJSON: bytes(2),
        authenticatorData: bytes(3),
        signature: bytes(4),
        userHandle: bytes(5),
      },
      getClientExtensionResults: () => ({}),
    } as unknown as PublicKeyCredential;
    const payload = authenticationCredential(credential);
    expect(payload.response).toEqual({
      clientDataJSON: "Ag",
      authenticatorData: "Aw",
      signature: "BA",
      userHandle: "BQ",
    });
    expect(payload.authenticatorAttachment).toBe("cross-platform");
  });
});

describe("WebAuthn", () => {
  it("degrades safely outside a browser", async () => {
    withoutNativeParser();
    expect(WebAuthn.isSupported()).toBe(false);
    expect(WebAuthn.isAvailable()).toBe(false);
    await expect(WebAuthn.clientCapabilities()).resolves.toEqual({});
    await expect(WebAuthn.isConditionalMediationAvailable()).resolves.toBe(false);
  });

  it("builds decoded options without optional undefined members", () => {
    const options = publicKeyCredentialCreationOptions(
      { name: "Example" },
      { id: bytes(1), name: "ada", displayName: "Ada" },
      bytes(2),
      [publicKeyCredentialParameters(-7)],
    );
    expect(options).not.toHaveProperty("timeout");
    expect(options).not.toHaveProperty("excludeCredentials");
  });
});
