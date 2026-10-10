"""Cliente de Gemini (SDK oficial google-genai). Timeout, detección de cuota, modelos de respaldo y consultas.

Mismo planteamiento que tenía la versión en Node: si el modelo principal ya no existe (404), se prueban los de
respaldo; un error de cuota o de red no cambia de modelo porque con la misma clave daría el mismo fallo.

Con herramientas (P9), Gemini puede pedir datos a la API antes de contestar: se ejecutan, se le devuelven y vuelve a
intentarlo, con un tope de consultas y de vueltas. Al llegar al tope se le obliga a contestar con lo que tiene.
"""

import logging
from typing import Literal, Protocol

from google import genai
from google.genai import errors, types

from .config import get_config

log = logging.getLogger("duende.gemini")

Codigo = Literal["sin_clave", "cuota", "bloqueado", "modelo_no_existe", "timeout", "vacio", "error"]

MOTIVOS_BLOQUEO = {"SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION", "IMAGE_SAFETY"}
# Vueltas con consultas antes de obligarle a contestar (cada vuelta puede traer varias consultas a la vez).
MAX_VUELTAS = 2
SIN_CONSULTAS = {"error": "Ya no quedan consultas para esta pregunta: contesta con lo que tienes."}


class Herramientas(Protocol):
    """Lo que necesita el bucle de consultas (la implementación está en herramientas.py)."""

    tool: types.Tool
    maximo: int

    def ejecutar(self, nombre: str, args: dict) -> dict: ...


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


def generar(
    sistema: str,
    contenidos: list[types.Content],
    temperatura: float = 0.8,
    herramientas: Herramientas | None = None,
) -> tuple[str, str]:
    """Devuelve (texto, modelo usado). Lanza GeminiError si no se puede generar."""
    cfg = get_config()
    ultimo: GeminiError | None = None
    for modelo in cfg.modelos():
        try:
            return _conversar(modelo, sistema, list(contenidos), temperatura, herramientas), modelo
        except GeminiError as err:
            if err.codigo != "modelo_no_existe":
                raise
            log.warning("%s no disponible, pruebo el siguiente modelo de respaldo", modelo)
            ultimo = err
    raise ultimo or GeminiError("error", "No hay ningún modelo de Gemini configurado")


def _conversar(
    modelo: str, sistema: str, contenidos: list[types.Content], temperatura: float, herramientas: Herramientas | None
) -> str:
    """Una respuesta con un modelo: si pide consultas, se ejecutan y vuelve a intentarlo, hasta el tope."""
    cfg = get_config()
    consultas = 0
    for vuelta in range(MAX_VUELTAS + 1):
        config = types.GenerateContentConfig(
            system_instruction=sistema,
            max_output_tokens=cfg.max_tokens,
            temperature=temperatura,
            safety_settings=_SEGURIDAD,
        )
        puede_consultar = herramientas is not None and vuelta < MAX_VUELTAS and consultas < herramientas.maximo
        if herramientas is not None:
            config.tools = [herramientas.tool]
            if not puede_consultar:
                # Se acabaron las consultas: a contestar con lo que hay.
                config.tool_config = types.ToolConfig(
                    function_calling_config=types.FunctionCallingConfig(mode=types.FunctionCallingConfigMode.NONE)
                )
        respuesta = _llamar(modelo, contenidos, config)
        pedidas = respuesta.function_calls if puede_consultar else None
        if not pedidas:
            return _texto(respuesta)

        assert herramientas is not None and respuesta.candidates and respuesta.candidates[0].content
        # Se le devuelve su propio mensaje tal cual (lleva las firmas de su razonamiento) y luego los resultados.
        contenidos.append(respuesta.candidates[0].content)
        partes = []
        for llamada in pedidas:
            if consultas < herramientas.maximo:
                consultas += 1
                resultado = herramientas.ejecutar(llamada.name or "", dict(llamada.args or {}))
            else:
                resultado = SIN_CONSULTAS
            partes.append(
                types.Part(
                    function_response=types.FunctionResponse(id=llamada.id, name=llamada.name, response=resultado)
                )
            )
        contenidos.append(types.Content(role="tool", parts=partes))
    raise GeminiError("vacio", "Gemini siguió pidiendo consultas sin contestar")


def _llamar(modelo: str, contenidos: list[types.Content], config: types.GenerateContentConfig):
    try:
        respuesta = _get_cliente().models.generate_content(model=modelo, contents=contenidos, config=config)
    except GeminiError:
        raise
    except Exception as err:  # noqa: BLE001 - el SDK lanza varios tipos; se traducen todos
        raise _traducir_error(err, modelo) from err
    bloqueo = getattr(respuesta.prompt_feedback, "block_reason", None) if respuesta.prompt_feedback else None
    if not bloqueo and respuesta.candidates:
        bloqueo = respuesta.candidates[0].finish_reason
    if bloqueo and str(getattr(bloqueo, "value", bloqueo)) in MOTIVOS_BLOQUEO:
        raise GeminiError("bloqueado", f"Respuesta bloqueada por Gemini ({bloqueo})")
    return respuesta


def _texto(respuesta: types.GenerateContentResponse) -> str:
    """El texto de la respuesta, sin el razonamiento. Vacío es un error: así contestan las reglas."""
    candidato = respuesta.candidates[0] if respuesta.candidates else None
    partes = candidato.content.parts if candidato and candidato.content and candidato.content.parts else []
    texto = "".join(p.text for p in partes if p.text and not p.thought).strip()
    if not texto:
        raise GeminiError("vacio", "Gemini devolvió una respuesta vacía")
    return texto
