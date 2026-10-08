import pytest
from google.genai import errors, types

from app import gemini
from app.config import get_config


class ModelosFalsos:
    """Hace de client.models: por cada modelo, devuelve una respuesta o lanza un error."""

    def __init__(self, guion: dict):
        self.guion = guion
        self.llamados: list[str] = []

    def generate_content(self, model, contents, config):
        self.llamados.append(model)
        resultado = self.guion[model]
        if isinstance(resultado, Exception):
            raise resultado
        return resultado


def respuesta(texto: str, fin=types.FinishReason.STOP) -> types.GenerateContentResponse:
    return types.GenerateContentResponse(
        candidates=[types.Candidate(content=types.Content(role="model", parts=[types.Part(text=texto)]), finish_reason=fin)]
    )


def no_existe(modelo: str) -> errors.ClientError:
    return errors.ClientError(404, {"error": {"code": 404, "message": f"models/{modelo} is not found", "status": "NOT_FOUND"}})


@pytest.fixture
def modelos(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    monkeypatch.setenv("GEMINI_MODEL", "principal")
    monkeypatch.setenv("GEMINI_FALLBACK_MODELS", "respaldo")
    get_config.cache_clear()

    def preparar(guion: dict) -> ModelosFalsos:
        falsos = ModelosFalsos(guion)
        monkeypatch.setattr(gemini, "_get_cliente", lambda: type("Cliente", (), {"models": falsos})())
        return falsos

    return preparar


CONTENIDO = [types.Content(role="user", parts=[types.Part(text="hola")])]


def test_si_el_modelo_ya_no_existe_prueba_el_de_respaldo(modelos):
    falsos = modelos({"principal": no_existe("principal"), "respaldo": respuesta("¡Buenas!")})
    assert gemini.generar("sistema", CONTENIDO) == ("¡Buenas!", "respaldo")
    assert falsos.llamados == ["principal", "respaldo"]


def test_sin_cuota_no_cambia_de_modelo(modelos):
    cuota = errors.ClientError(429, {"error": {"code": 429, "message": "RESOURCE_EXHAUSTED", "status": "RESOURCE_EXHAUSTED"}})
    falsos = modelos({"principal": cuota, "respaldo": respuesta("no debería llegar")})
    with pytest.raises(gemini.GeminiError) as e:
        gemini.generar("sistema", CONTENIDO)
    assert e.value.codigo == "cuota"
    assert falsos.llamados == ["principal"]


def test_respuesta_bloqueada_o_vacia(modelos):
    modelos({"principal": respuesta("", fin=types.FinishReason.SAFETY)})
    with pytest.raises(gemini.GeminiError) as e:
        gemini.generar("sistema", CONTENIDO)
    assert e.value.codigo == "bloqueado"


def test_sin_clave(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "")
    get_config.cache_clear()
    monkeypatch.setattr(gemini, "_cliente", None)
    with pytest.raises(gemini.GeminiError) as e:
        gemini.generar("sistema", CONTENIDO)
    assert e.value.codigo == "sin_clave"
