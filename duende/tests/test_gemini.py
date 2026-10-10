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


# ─── Consultas a la API (P9) ─────────────────────────────────────────────────


class Conversacion:
    """Hace de client.models con una respuesta por llamada, en orden; apunta lo que recibe cada vez."""

    def __init__(self, respuestas: list):
        self.respuestas = list(respuestas)
        self.llamadas: list[tuple[list, types.GenerateContentConfig]] = []

    def generate_content(self, model, contents, config):
        self.llamadas.append((list(contents), config))
        return self.respuestas.pop(0)


def pide(*llamadas: tuple[str, dict]) -> types.GenerateContentResponse:
    """Respuesta de Gemini que pide consultas en vez de contestar."""
    partes = [types.Part(function_call=types.FunctionCall(id=f"c{i}", name=n, args=a)) for i, (n, a) in enumerate(llamadas)]
    return types.GenerateContentResponse(candidates=[types.Candidate(content=types.Content(role="model", parts=partes))])


class HerramientasFalsas:
    def __init__(self, maximo: int = 4):
        self.tool = types.Tool(function_declarations=[types.FunctionDeclaration(name="buscar_partidas")])
        self.maximo = maximo
        self.ejecutadas: list[tuple[str, dict]] = []

    def ejecutar(self, nombre, args):
        self.ejecutadas.append((nombre, args))
        return {"resultado": {"partidas": len(self.ejecutadas)}}


@pytest.fixture
def conversacion(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    monkeypatch.setenv("GEMINI_MODEL", "principal")
    monkeypatch.setenv("GEMINI_FALLBACK_MODELS", "")
    get_config.cache_clear()

    def preparar(respuestas: list) -> Conversacion:
        falsa = Conversacion(respuestas)
        monkeypatch.setattr(gemini, "_get_cliente", lambda: type("Cliente", (), {"models": falsa})())
        return falsa

    return preparar


def modo(config: types.GenerateContentConfig):
    return config.tool_config.function_calling_config.mode if config.tool_config else None


def test_consulta_la_api_y_contesta_con_lo_que_le_devuelve(conversacion):
    falsa = conversacion([pide(("buscar_partidas", {"jugador": "Ana", "mapa_o_dios": "Mirage"})), respuesta("En Mirage, regular.")])
    h = HerramientasFalsas()

    assert gemini.generar("sistema", CONTENIDO, herramientas=h) == ("En Mirage, regular.", "principal")

    assert h.ejecutadas == [("buscar_partidas", {"jugador": "Ana", "mapa_o_dios": "Mirage"})]
    contenidos, config = falsa.llamadas[1]
    # Su propio mensaje con la consulta, y la respuesta de la API con el mismo id y nombre.
    assert contenidos[1].role == "model" and contenidos[1].parts[0].function_call.name == "buscar_partidas"
    devuelta = contenidos[2].parts[0].function_response
    assert (contenidos[2].role, devuelta.id, devuelta.name) == ("tool", "c0", "buscar_partidas")
    assert devuelta.response == {"resultado": {"partidas": 1}}
    assert config.tools == [h.tool] and modo(config) is None
    assert len(CONTENIDO) == 1  # la conversación original no se toca


def test_con_el_tope_de_consultas_se_le_obliga_a_contestar(conversacion):
    # Pide tres a la vez con un tope de dos: la tercera vuelve como error y la siguiente llamada ya no deja consultar.
    falsa = conversacion([
        pide(("buscar_partidas", {"n": 1}), ("buscar_partidas", {"n": 2}), ("buscar_partidas", {"n": 3})),
        respuesta("Con lo que hay."),
    ])
    h = HerramientasFalsas(maximo=2)
    assert gemini.generar("sistema", CONTENIDO, herramientas=h)[0] == "Con lo que hay."
    assert [a for _, a in h.ejecutadas] == [{"n": 1}, {"n": 2}]
    devueltas = [p.function_response.response for p in falsa.llamadas[1][0][2].parts]
    assert devueltas[2] == gemini.SIN_CONSULTAS
    assert modo(falsa.llamadas[1][1]) == types.FunctionCallingConfigMode.NONE


def test_tras_las_vueltas_maximas_tiene_que_contestar(conversacion):
    falsa = conversacion([pide(("buscar_partidas", {}))] * gemini.MAX_VUELTAS + [pide(("buscar_partidas", {}))])
    h = HerramientasFalsas()
    with pytest.raises(gemini.GeminiError) as e:  # si aun así no contesta, responden las reglas
        gemini.generar("sistema", CONTENIDO, herramientas=h)
    assert e.value.codigo == "vacio"
    assert len(h.ejecutadas) == gemini.MAX_VUELTAS
    assert [modo(c) for _, c in falsa.llamadas] == [None] * gemini.MAX_VUELTAS + [types.FunctionCallingConfigMode.NONE]


def test_sin_herramientas_no_se_declaran(conversacion):
    falsa = conversacion([respuesta("Hola.")])
    assert gemini.generar("sistema", CONTENIDO) == ("Hola.", "principal")
    assert falsa.llamadas[0][1].tools is None


def test_sin_clave(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "")
    get_config.cache_clear()
    monkeypatch.setattr(gemini, "_cliente", None)
    with pytest.raises(gemini.GeminiError) as e:
        gemini.generar("sistema", CONTENIDO)
    assert e.value.codigo == "sin_clave"
