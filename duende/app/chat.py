"""Chat del Duende. Con GOOGLE_API_KEY contesta Gemini; sin clave, sin cuota o si Gemini falla, contestan las reglas.

Gemini recibe los datos ya resumidos y las recomendaciones calculadas por el motor, y la conversación tal cual.
Las respuestas se cachean por hash de la petición y hay un límite diario de llamadas nuevas.
"""

import hashlib
import logging
import threading
import time
from collections import OrderedDict, deque

from google.genai import types

from . import gemini, reglas_chat
from .config import get_config
from .modelos import JugadorContexto, PeticionChat, RespuestaChat
from .personalidad import prompt_sistema

log = logging.getLogger("duende.chat")

DIA = 24 * 60 * 60
MAX_CACHE = 300


class _Limite:
    """Llamadas a Gemini en las últimas 24 h, en memoria (se reinicia con el proceso)."""

    def __init__(self) -> None:
        self._llamadas: deque[float] = deque()
        self._lock = threading.Lock()

    def disponible(self, limite: int) -> bool:
        with self._lock:
            ahora = time.time()
            while self._llamadas and self._llamadas[0] < ahora - DIA:
                self._llamadas.popleft()
            return len(self._llamadas) < limite

    def registrar(self) -> None:
        with self._lock:
            self._llamadas.append(time.time())


class _Cache:
    def __init__(self) -> None:
        self._datos: OrderedDict[str, tuple[str, str]] = OrderedDict()
        self._lock = threading.Lock()

    def get(self, clave: str) -> tuple[str, str] | None:
        with self._lock:
            valor = self._datos.get(clave)
            if valor:
                self._datos.move_to_end(clave)
            return valor

    def set(self, clave: str, valor: tuple[str, str]) -> None:
        with self._lock:
            self._datos[clave] = valor
            self._datos.move_to_end(clave)
            while len(self._datos) > MAX_CACHE:
                self._datos.popitem(last=False)


limite = _Limite()
cache = _Cache()


def _datos_para_prompt(p: PeticionChat) -> dict:
    """Lo que ve Gemini: los jugadores del foco con todo y sus recomendaciones; del resto, solo lo básico."""

    def juego_basico(g) -> dict:
        basico = g.resumen.model_dump(by_alias=True, exclude_none=True)
        if g.rol:
            basico["rol"] = g.rol
        if g.periodos:
            # Para "¿quién va mejor esta semana?" también hacen falta los últimos días del resto.
            basico["periodos"] = [pr.model_dump(by_alias=True, exclude_none=True, exclude={"equipo"}) for pr in g.periodos]
        return basico

    def jugador_completo(j: JugadorContexto) -> dict:
        return {
            "nombre": j.nombre,
            "juegos": [
                {
                    **g.model_dump(by_alias=True, exclude_none=True),
                    "recomendaciones": [
                        {"nivel": i.nivel, "titulo": i.titulo, "texto": i.texto, "consejo": i.consejo}
                        for i in reglas_chat.insights_de(j, g, p.lang)
                    ],
                }
                for g in j.juegos
            ],
        }

    foco = [j for s in p.foco for j in p.equipo if j.slug == s]
    return {
        "idioma": p.lang,
        "juego_seleccionado": p.juego,
        "periodo_seleccionado": p.periodo,
        "foco": [jugador_completo(j) for j in foco],
        "equipo": [
            {"nombre": j.nombre, "juegos": [juego_basico(g) for g in j.juegos]} for j in p.equipo if j not in foco
        ]
        if foco
        else [jugador_completo(j) for j in p.equipo],
    }


def _contenidos(p: PeticionChat) -> list[types.Content]:
    """Historial para Gemini: empieza por el usuario y no repite rol seguido (se juntan los mensajes)."""
    contenidos: list[types.Content] = []
    for m in p.mensajes:
        rol = "user" if m.rol == "usuario" else "model"
        if not contenidos and rol == "model":
            continue
        if contenidos and contenidos[-1].role == rol:
            contenidos[-1].parts.append(types.Part(text=m.texto))
        else:
            contenidos.append(types.Content(role=rol, parts=[types.Part(text=m.texto)]))
    return contenidos


def _clave(p: PeticionChat) -> str:
    return hashlib.sha256(p.model_dump_json(by_alias=True).encode()).hexdigest()[:32]


def responder(p: PeticionChat) -> RespuestaChat:
    cfg = get_config()
    sugerencias = reglas_chat.sugerencias(p)

    if cfg.gemini_configurado:
        clave = _clave(p)
        guardada = cache.get(clave)
        if guardada:
            texto, modelo = guardada
            return RespuestaChat(respuesta=texto, origen="gemini", modelo=modelo, sugerencias=sugerencias)
        contenidos = _contenidos(p)
        if contenidos and limite.disponible(cfg.daily_limit):
            try:
                texto, modelo = gemini.generar(prompt_sistema(p.lang, _datos_para_prompt(p)), contenidos)
                limite.registrar()
                cache.set(clave, (texto, modelo))
                return RespuestaChat(respuesta=texto, origen="gemini", modelo=modelo, sugerencias=sugerencias)
            except gemini.GeminiError as err:
                log.warning("Gemini no disponible (%s): %s. Respondo con reglas.", err.codigo, err)
        elif contenidos:
            log.warning("Límite diario de Gemini alcanzado (%s). Respondo con reglas.", cfg.daily_limit)

    return RespuestaChat(respuesta=reglas_chat.responder(p), origen="reglas", sugerencias=sugerencias)
