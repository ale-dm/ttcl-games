"""Cliente de Gemini (SDK oficial google-genai). Timeout, detección de cuota y modelos de respaldo.

Mismo planteamiento que tenía la versión en Node: si el modelo principal ya no existe (404), se prueban los de
respaldo; un error de cuota o de red no cambia de modelo porque con la misma clave daría el mismo fallo.
"""

import logging
from typing import Literal

from google import genai
from google.genai import errors, types

from .config import get_config

log = logging.getLogger("duende.gemini")

Codigo = Literal["sin_clave", "cuota", "bloqueado", "modelo_no_existe", "timeout", "vacio", "error"]

MOTIVOS_BLOQUEO = {"SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION", "IMAGE_SAFETY"}


class GeminiError(Exception):
    def __init__(self, codigo: Codigo, mensaje: str):
        super().__init__(mensaje)
        self.codigo = codigo


_cliente: genai.Client | None = None


def _get_cliente() -> genai.Client:
    global _cliente
    cfg = get_config()
    if not cfg.google_api_key:
        raise GeminiError("sin_clave", "Falta GOOGLE_API_KEY en el entorno")
    if _cliente is None:
        _cliente = genai.Client(
            api_key=cfg.google_api_key,
            http_options=types.HttpOptions(timeout=cfg.gemini_timeout_ms),
        )
    return _cliente


# Se bloquea solo lo muy fuerte: el Duende pica, pero no debe generar nada que cruce de verdad.
_SEGURIDAD = [
    types.SafetySetting(category=categoria, threshold=types.HarmBlockThreshold.BLOCK_ONLY_HIGH)
    for categoria in (
        types.HarmCategory.HARM_CATEGORY_HARASSMENT,
        types.HarmCategory.HARM_CATEGORY_HATE_SPEECH,
        types.HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT,
        types.HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT,
    )
]


def _traducir_error(err: Exception, modelo: str) -> GeminiError:
    if isinstance(err, errors.APIError):
        codigo = getattr(err, "code", None)
        mensaje = str(getattr(err, "message", "") or err)
        if codigo == 429 or "RESOURCE_EXHAUSTED" in mensaje or "quota" in mensaje.lower():
            return GeminiError("cuota", "Gemini sin cuota o con límite de peticiones")
        if codigo == 404 or "not found" in mensaje.lower() or "is not supported" in mensaje.lower():
            return GeminiError("modelo_no_existe", f"El modelo {modelo} no existe o ya no está disponible")
        return GeminiError("error", mensaje.split("\n")[0])
    if "timeout" in type(err).__name__.lower() or "timed out" in str(err).lower():
        return GeminiError("timeout", f"Gemini no respondió en {get_config().gemini_timeout_ms} ms")
    return GeminiError("error", str(err).split("\n")[0])


def generar(sistema: str, contenidos: list[types.Content], temperatura: float = 0.8) -> tuple[str, str]:
    """Devuelve (texto, modelo usado). Lanza GeminiError si no se puede generar."""
    cfg = get_config()
    ultimo: GeminiError | None = None
    for modelo in cfg.modelos():
        try:
            respuesta = _get_cliente().models.generate_content(
                model=modelo,
                contents=contenidos,
                config=types.GenerateContentConfig(
                    system_instruction=sistema,
                    max_output_tokens=cfg.max_tokens,
                    temperature=temperatura,
                    safety_settings=_SEGURIDAD,
                ),
            )
        except GeminiError:
            raise
        except Exception as err:  # noqa: BLE001 - el SDK lanza varios tipos; se traducen todos
            ultimo = _traducir_error(err, modelo)
            if ultimo.codigo == "modelo_no_existe":
                log.warning("%s no disponible, pruebo el siguiente modelo de respaldo", modelo)
                continue
            raise ultimo from err

        bloqueo = getattr(respuesta.prompt_feedback, "block_reason", None) if respuesta.prompt_feedback else None
        if not bloqueo and respuesta.candidates:
            bloqueo = respuesta.candidates[0].finish_reason
        if bloqueo and str(getattr(bloqueo, "value", bloqueo)) in MOTIVOS_BLOQUEO:
            raise GeminiError("bloqueado", f"Respuesta bloqueada por Gemini ({bloqueo})")
        texto = (respuesta.text or "").strip()
        if not texto:
            raise GeminiError("vacio", "Gemini devolvió una respuesta vacía")
        return texto, modelo
    raise ultimo or GeminiError("error", "No hay ningún modelo de Gemini configurado")
