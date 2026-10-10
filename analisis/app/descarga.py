"""De dónde sale la demo: una URL (la firmada de FACEIT) o un fichero de la carpeta de demos. Se descomprime si hace
falta (.gz, .zst o .bz2, se mira por los primeros bytes) y todo lo temporal se borra al acabar: las demos no se
guardan. Las de la carpeta no se tocan.
"""

import bz2
import gzip
import shutil
import tempfile
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path

import httpx
import zstandard

from .config import Config

GZIP = b"\x1f\x8b"
ZSTD = b"\x28\xb5\x2f\xfd"
BZIP2 = b"BZh"
BLOQUE = 1024 * 1024


class DemoNoValida(Exception):
    """La demo no se puede usar (no existe, es demasiado grande, no es una demo...). Es culpa de la demo, no del
    trabajador: la API no la vuelve a pedir."""


class DescargaFallida(Exception):
    """No se ha podido descargar ahora (red, el servidor de demos no responde): se puede intentar más tarde."""


def en_carpeta(cfg: Config, nombre: str) -> Path:
    """La ruta de un fichero de la carpeta de demos; nada de salirse de ella."""
    ruta = (cfg.carpeta / nombre).resolve()
    if cfg.carpeta not in ruta.parents or not ruta.is_file():
        raise DemoNoValida(f"No hay ninguna demo «{nombre}» en la carpeta de demos.")
    return ruta


def descargar(url: str, destino: Path, cfg: Config, cliente: httpx.Client | None = None) -> None:
    if not url.startswith(("https://", "http://")):
        raise DemoNoValida("La URL de la demo tiene que ser http o https.")
    http = cliente or httpx.Client(follow_redirects=True, timeout=cfg.timeout_s)
    try:
        with http.stream("GET", url) as r:
            if r.status_code in (401, 403, 404, 410):
                raise DemoNoValida(f"El servidor de demos responde {r.status_code} (caducada o sin permiso).")
            if r.status_code >= 400:
                raise DescargaFallida(f"El servidor de demos responde {r.status_code}.")
            total = 0
            with destino.open("wb") as f:
                for trozo in r.iter_bytes(BLOQUE):
                    total += len(trozo)
                    if total > cfg.max_bytes:
                        raise DemoNoValida(f"La demo pasa de {cfg.max_mb} MB.")
                    f.write(trozo)
    except httpx.HTTPError as err:
        raise DescargaFallida(f"No se pudo descargar la demo ({type(err).__name__}).") from err
    finally:
        if cliente is None:
            http.close()


def descomprimir(origen: Path, destino: Path, cfg: Config) -> Path:
    """La demo sin comprimir: `origen` si ya lo está, o `destino` con su contenido descomprimido."""
    with origen.open("rb") as f:
        cabecera = f.read(4)
    if cabecera.startswith(GZIP):
        abrir = gzip.open
    elif cabecera.startswith(ZSTD):
        def abrir(ruta: Path, modo: str = "rb"):  # type: ignore[misc]
            return zstandard.ZstdDecompressor().stream_reader(ruta.open(modo), closefd=True)
    elif cabecera.startswith(BZIP2):
        abrir = bz2.open
    else:
        return origen
    total = 0
    try:
        with abrir(origen, "rb") as entrada, destino.open("wb") as salida:
            while trozo := entrada.read(BLOQUE):
                total += len(trozo)
                if total > cfg.max_bytes:
                    raise DemoNoValida(f"La demo descomprimida pasa de {cfg.max_mb} MB.")
                salida.write(trozo)
    except (OSError, EOFError, zstandard.ZstdError) as err:
        raise DemoNoValida("La demo está rota o no se puede descomprimir.") from err
    return destino


def es_demo_cs2(ruta: Path) -> bool:
    """Las demos de CS2 (Source 2) empiezan por PBDEMS2."""
    with ruta.open("rb") as f:
        return f.read(8).startswith(b"PBDEMS2")


@contextmanager
def demo_local(cfg: Config, url: str | None = None, archivo: str | None = None) -> Iterator[Path]:
    """La demo lista para leer. Lo descargado y lo descomprimido se borran al salir."""
    if bool(url) == bool(archivo):
        raise DemoNoValida("Hace falta la URL de la demo o el nombre del fichero, uno de los dos.")
    temporal = Path(tempfile.mkdtemp(prefix="ttcl-demo-"))
    try:
        if url:
            origen = temporal / "descargada"
            descargar(url, origen, cfg)
        else:
            origen = en_carpeta(cfg, archivo or "")
        demo = descomprimir(origen, temporal / "demo.dem", cfg)
        if not es_demo_cs2(demo):
            raise DemoNoValida("El fichero no es una demo de CS2.")
        yield demo
    finally:
        shutil.rmtree(temporal, ignore_errors=True)
