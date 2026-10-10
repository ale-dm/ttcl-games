import httpx
import pytest

from app import api_ttcl
from app.herramientas import MAX_PARTIDAS, Herramientas
from app.modelos import Mensaje, PeticionChat

from .conftest import jugador, resumen_cs2


class ApiFalsa:
    """Hace de API Java: apunta cada petición y contesta lo que diga `respuestas` (por ruta)."""

    def __init__(self, respuestas: dict[str, tuple[int, object]]):
        self.respuestas = respuestas
        self.peticiones: list[httpx.Request] = []

    def __call__(self, peticion: httpx.Request) -> httpx.Response:
        self.peticiones.append(peticion)
        estado, cuerpo = self.respuestas.get(peticion.url.path, (404, {"error": "No existe."}))
        return httpx.Response(estado, json=cuerpo)


@pytest.fixture
def api(monkeypatch):
    def preparar(respuestas: dict[str, tuple[int, object]]) -> ApiFalsa:
        falsa = ApiFalsa(respuestas)
        cliente = httpx.Client(base_url="http://api", transport=httpx.MockTransport(falsa))
        monkeypatch.setattr(api_ttcl, "_get_cliente", lambda: cliente)
        return falsa

    return preparar


def herramientas(maximo: int = 4) -> Herramientas:
    equipo = [jugador("j1", "Ana", resumen_cs2()), jugador("j2", "Bea Ruiz", resumen_cs2())]
    p = PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="x")], equipo=equipo)
    return Herramientas(p, maximo)


PARTIDA = {"partidaId": 7, "juego": "cs2", "jugadaEn": "2026-10-09T20:00:00Z", "modo": "de_mirage", "gano": False,
           "kills": 12, "muertes": 18, "asistencias": 3, "datos": {"mapa": "de_mirage", "adr": 70.1},
           "companeros": ["Bea Ruiz"]}


def test_declaraciones_con_los_nombres_del_equipo():
    tool = herramientas().tool
    nombres = [f.name for f in tool.function_declarations]
    assert nombres == ["buscar_partidas", "desglose", "comparar", "sinergias"]
    buscar = tool.function_declarations[0].parameters_json_schema
    assert buscar["properties"]["jugador"]["enum"] == ["Ana", "Bea Ruiz"]
    assert buscar["required"] == ["jugador", "juego"]


def test_buscar_partidas_pide_la_consulta_filtrada_y_recorta_lo_que_sobra(api):
    falsa = api({"/api/jugadores/j1/consulta": (200, {"juego": "cs2", "resumen": {"partidas": 3},
                                                       "equipo": None, "partidas": [PARTIDA]})})
    h = herramientas()
    r = h.ejecutar("buscar_partidas", {"jugador": "ana", "juego": "cs2", "mapa_o_dios": "Mirage",
                                       "resultado": "derrota", "desde": "2026-10-01", "ultimas": 2.0})
    assert r == {"resultado": {"resumen": {"partidas": 3}, "equipo": None, "partidas": [
        {k: v for k, v in PARTIDA.items() if k not in ("partidaId", "juego")}]}}
    params = dict(falsa.peticiones[0].url.params)
    assert params == {"juego": "cs2", "clave": "Mirage", "resultado": "derrota", "desde": "2026-10-01",
                      "ultimas": "2", "limite": str(MAX_PARTIDAS)}
    assert h.usadas == ["buscar_partidas"]


def test_desglose_comparar_y_sinergias(api):
    falsa = api({
        "/api/jugadores/j2/juegos/cs2": (200, {"resumen": {"partidas": 9}, "desglose": [{"clave": "de_nuke"}],
                                               "serie": [1, 2, 3]}),
        "/api/comparar": (200, {"a": {"slug": "j1", "nombre": "Ana"}, "b": {"slug": "j2", "nombre": "Bea Ruiz"},
                                "resumenA": {"partidas": 5}, "resumenB": None, "filas": []}),
        "/api/jugadores/j1/sinergias": (200, {"solo": None, "companeros": []}),
    })
    h = herramientas()
    assert h.ejecutar("desglose", {"jugador": "Bea Ruiz", "juego": "cs2", "periodo": "30d"}) == {
        "resultado": {"partidas": 9, "desglose": [{"clave": "de_nuke"}]}}  # sin la serie de la gráfica
    assert h.ejecutar("comparar", {"jugador_a": "Ana", "jugador_b": "j2", "juego": "cs2", "periodo": "todo"}) == {
        "resultado": {"a": "Ana", "b": "Bea Ruiz", "partidas_a": 5, "partidas_b": 0, "filas": []}}
    assert h.ejecutar("sinergias", {"jugador": "Ana", "juego": "cs2", "periodo": "7d"}) == {
        "resultado": {"solo": None, "companeros": []}}
    assert dict(falsa.peticiones[0].url.params) == {"periodo": "30d"}
    assert dict(falsa.peticiones[1].url.params) == {"a": "j1", "b": "j2", "juego": "cs2"}  # "todo" no se manda
    assert dict(falsa.peticiones[2].url.params) == {"juego": "cs2", "periodo": "7d"}


def test_lo_que_no_vale_vuelve_como_error_sin_romper_nada(api):
    api({"/api/jugadores/j1/consulta": (400, {"error": "Petición no válida."})})
    h = herramientas()
    assert h.ejecutar("buscar_partidas", {"jugador": "Carla", "juego": "cs2"}) == {
        "error": "No hay nadie en el equipo que se llame Carla."}
    assert "Juego desconocido" in h.ejecutar("desglose", {"jugador": "Ana", "juego": "valorant"})["error"]
    assert h.ejecutar("buscar_partidas", {"jugador": "Ana", "juego": "cs2"}) == {"error": "Petición no válida."}
    assert "error" in h.ejecutar("sinergias", {"jugador": "Ana"})  # falta el juego
    assert h.ejecutar("borrar_todo", {}) == {"error": "No existe la herramienta borrar_todo."}


def test_sin_api_tambien_es_un_error(monkeypatch):
    def caida(peticion):
        raise httpx.ConnectError("sin conexión", request=peticion)

    cliente = httpx.Client(base_url="http://api", transport=httpx.MockTransport(caida))
    monkeypatch.setattr(api_ttcl, "_get_cliente", lambda: cliente)
    assert herramientas().ejecutar("sinergias", {"jugador": "Ana", "juego": "cs2"}) == {
        "error": "la API no responde (ConnectError)"}


def test_la_misma_consulta_en_menos_de_un_minuto_sale_de_la_cache(api):
    falsa = api({"/api/jugadores/j1/sinergias": (200, {"solo": None, "companeros": []})})
    h = herramientas()
    h.ejecutar("sinergias", {"jugador": "Ana", "juego": "cs2"})
    h.ejecutar("sinergias", {"jugador": "ana", "juego": "cs2", "periodo": "todo"})  # la misma consulta
    assert len(falsa.peticiones) == 1
    h.ejecutar("sinergias", {"jugador": "Ana", "juego": "cs2", "periodo": "7d"})
    assert len(falsa.peticiones) == 2
