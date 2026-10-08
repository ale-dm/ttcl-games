import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { candidatosDeModelo, isQuotaError } from "@/lib/gemini";
import { getConfig, resetConfigForTests } from "@/lib/config";

const entornoOriginal = { ...process.env };

beforeEach(() => {
  process.env.DATABASE_URL = "postgres://localhost/test";
  process.env.GEMINI_MODEL = "gemini-2.5-flash";
  process.env.GEMINI_FALLBACK_MODELS = "gemini-2.5-flash, gemini-2.5-pro";
  resetConfigForTests();
});

afterEach(() => {
  process.env = { ...entornoOriginal };
  resetConfigForTests();
});

describe("Gemini", () => {
  it("el modelo principal va primero y los de respaldo no se repiten", () => {
    expect(candidatosDeModelo()).toEqual(["gemini-2.5-flash", "gemini-2.5-pro"]);
    expect(candidatosDeModelo("gemini-x")).toEqual(["gemini-x", "gemini-2.5-flash", "gemini-2.5-pro"]);
  });

  it("reconoce errores de cuota por código o por texto", () => {
    expect(isQuotaError({ status: 429 })).toBe(true);
    expect(isQuotaError(new Error("RESOURCE_EXHAUSTED: quota exceeded"))).toBe(true);
    expect(isQuotaError(new Error("Bad request"))).toBe(false);
  });

  it("la configuración valida los números y da un error claro si faltan variables", () => {
    delete process.env.DATABASE_URL;
    resetConfigForTests();
    expect(() => getConfig()).toThrow(/DATABASE_URL/);
  });
});
