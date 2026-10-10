"""Textos de las recomendaciones, en español e inglés, con la voz del Duende: pica, pero por lo que pasa en el juego.

Cada entrada tiene `titulo`, `frase` (el pique) y `consejo`. El consejo puede depender del juego: entonces es un
diccionario {juego: texto}. La comparación con números ("Tienes X; el equipo, Y") la añade insights.py.
"""

from .modelos import Idioma, Juego

T = dict[str, str | dict[str, str]]

# ─── Debilidades por métrica ─────────────────────────────────────────────────

DEBILIDADES: dict[str, dict[Idioma, T]] = {
    "winrate": {
        "es": {
            "titulo": "El winrate está en rojo",
            "frase": "Pierdes más de lo que ganas, y eso no lo arregla ninguna estadística bonita.",
            "consejo": {
                "cs2": "Hablad la economía y la estrategia antes de cada mitad. Una ronda bien comprada vale más que tres ecos tirados.",
                "smite2": "Juega los objetivos: torres, Fénix, Gold Fury y Fire Giant ganan partidas; las kills sueltas, no.",
            },
        },
        "en": {
            "titulo": "Your win rate is in the red",
            "frase": "You lose more than you win, and no pretty stat fixes that.",
            "consejo": {
                "cs2": "Talk economy and a plan before each half. One well-bought round beats three thrown ecos.",
                "smite2": "Play objectives: towers, phoenixes, Gold Fury and Fire Giant win games; stray kills don't.",
            },
        },
    },
    "kd": {
        "es": {
            "titulo": "Mueres más de lo que matas",
            "frase": "El K/D no miente: cada duelo que pierdes es un compañero jugando en inferioridad.",
            "consejo": {
                "cs2": "Juega posiciones con apoyo y no repitas el mismo ángulo después de dar información. Vivir también es aportar.",
                "smite2": "Pelea cuando tu equipo esté cerca y retírate con la barra de vida a medias, no con un pixel.",
            },
        },
        "en": {
            "titulo": "You die more than you kill",
            "frase": "K/D doesn't lie: every duel you lose leaves your team playing a man down.",
            "consejo": {
                "cs2": "Hold positions with support and don't re-peek the same angle after giving info. Staying alive is a contribution.",
                "smite2": "Fight when your team is close and back off at half health, not at one pixel.",
            },
        },
    },
    "adr": {
        "es": {
            "titulo": "Poco daño por ronda",
            "frase": "Con ese ADR, los rivales casi ni se enteran de que estás en la partida.",
            "consejo": "Participa en más duelos útiles y usa HE y molotov en las ejecuciones: el daño de utilidad también cuenta.",
        },
        "en": {
            "titulo": "Low damage per round",
            "frase": "With that ADR the enemy barely notices you're in the server.",
            "consejo": "Take more useful duels and throw HEs and molotovs on executes: utility damage counts too.",
        },
    },
    "hs_pct": {
        "es": {
            "titulo": "Pocos headshots",
            "frase": "Disparas mucho al pecho. Las balas son gratis, el tiempo para matar no.",
            "consejo": "Lleva la mira siempre a altura de cabeza (crosshair placement) y haz 10 minutos de deathmatch solo a la cabeza antes de jugar.",
        },
        "en": {
            "titulo": "Not enough headshots",
            "frase": "Lots of chest shots. Bullets are free; time-to-kill isn't.",
            "consejo": "Keep your crosshair at head height at all times and warm up with 10 minutes of headshot-only deathmatch.",
        },
    },
    "kr": {
        "es": {
            "titulo": "Poco impacto por ronda",
            "frase": "Hay rondas enteras en las que pasas sin dejar huella.",
            "consejo": "Busca un pick temprano con apoyo de un compañero, o juega de trade justo detrás del que entra.",
        },
        "en": {
            "titulo": "Low impact per round",
            "frase": "Whole rounds go by without you leaving a mark.",
            "consejo": "Look for an early pick with a teammate's support, or play the trade right behind your entry.",
        },
    },
    "entry_pct": {
        "es": {
            "titulo": "Las entradas te salen caras",
            "frase": "Abres la ronda… y la abres para el otro equipo.",
            "consejo": "No entres en seco: pide una flash o un humo antes. Si no te sale, deja el entry a otro y juega de trade.",
        },
        "en": {
            "titulo": "Your entries cost you",
            "frase": "You open the round… for the other team.",
            "consejo": "Don't dry-peek: ask for a flash or a smoke first. If it still doesn't work, let someone else entry and play the trade.",
        },
    },
    "clutch_pct": {
        "es": {
            "titulo": "Los clutch se te escapan",
            "frase": "Cuando te quedas solo, la ronda se da por perdida antes de tiempo.",
            "consejo": "En 1vX juega con el tiempo y el sonido, aísla los duelos de uno en uno y no te muevas si no hace falta.",
        },
        "en": {
            "titulo": "Clutches slip away",
            "frase": "When you're the last one standing, the round is lost early.",
            "consejo": "In 1vX play the clock and sound, isolate duels one at a time and don't move unless you must.",
        },
    },
    "kills_media": {
        "es": {
            "titulo": "Kills / partida: por debajo del equipo",
            "frase": "Los demás se llevan las kills y tú te llevas el viaje.",
            "consejo": {
                "cs2": "Colócate donde llegue la acción (o pide un rol con más duelos) y aprovecha los trades de tus compañeros.",
                "smite2": "Rota antes a las peleas y guarda el control para cuando el rival use su escape.",
            },
        },
        "en": {
            "titulo": "Kills / match: below the team",
            "frase": "The others take the kills, you take the ride.",
            "consejo": {
                "cs2": "Play where the action arrives (or ask for a role with more duels) and take your teammates' trades.",
                "smite2": "Rotate to fights earlier and save your crowd control for when the enemy uses their escape.",
            },
        },
    },
    "muertes_media": {
        "es": {
            "titulo": "Mueres demasiado",
            "frase": "Pasas más tiempo mirando la partida que jugándola.",
            "consejo": {
                "cs2": "No asomes solo sin información: espera a un compañero para el trade y cambia de posición tras cada kill.",
                "smite2": "Pon wards, no te pases de la mitad del mapa sin visión y compra defensas a tiempo.",
            },
        },
        "en": {
            "titulo": "You die too much",
            "frase": "You spend more time spectating than playing.",
            "consejo": {
                "cs2": "Don't peek alone without info: wait for a teammate to trade and reposition after every kill.",
                "smite2": "Place wards, don't overextend without vision and buy defense on time.",
            },
        },
    },
    "asistencias_media": {
        "es": {
            "titulo": "Asistencias / partida: por debajo del equipo",
            "frase": "Juegas muy por tu cuenta.",
            "consejo": {
                "cs2": "Flashea para los demás y juega cerca del que entra: las asistencias ganan rondas aunque no salgan en el killfeed.",
                "smite2": "Mira el minimapa y rota con tu jungla: llegar a la pelea a tiempo vale más que la kill.",
            },
        },
        "en": {
            "titulo": "Assists / match: below the team",
            "frase": "You play very much on your own.",
            "consejo": {
                "cs2": "Flash for others and stay close to your entry: assists win rounds even if they don't show in the killfeed.",
                "smite2": "Watch the minimap and rotate with your jungler: arriving on time beats getting the kill.",
            },
        },
    },
    "dano_utilidad": {
        "es": {
            "titulo": "La utilidad se queda en el bolsillo",
            "frase": "Compras granadas para morir con ellas puestas.",
            "consejo": "Aprende dos molotov y dos HE por mapa para retrasar empujes y rematar a quien ya está tocado.",
        },
        "en": {
            "titulo": "Your utility stays in your pocket",
            "frase": "You buy grenades just to die holding them.",
            "consejo": "Learn two molotovs and two HE lineups per map to stall pushes and finish off damaged enemies.",
        },
    },
    "kda": {
        "es": {
            "titulo": "KDA flojo",
            "frase": "Entre muertes y poca participación, el KDA está pidiendo auxilio.",
            "consejo": "Elige mejor las peleas: entra cuando el rival haya gastado sus habilidades y sal antes de quedarte sin escape.",
        },
        "en": {
            "titulo": "Weak KDA",
            "frase": "Between deaths and low participation, your KDA is calling for help.",
            "consejo": "Pick your fights: go in after the enemy burns their abilities and leave before your escape is down.",
        },
    },
    "dano_min": {
        "es": {
            "titulo": "Poco daño por minuto",
            "frase": "Las peleas pasan y tu barra de daño apenas se mueve.",
            "consejo": "Revisa la build (poder y penetración a tiempo) y llega antes a las peleas para soltar todas las habilidades.",
        },
        "en": {
            "titulo": "Low damage per minute",
            "frase": "Fights happen and your damage bar barely moves.",
            "consejo": "Check your build (power and penetration on time) and reach fights earlier to land all your abilities.",
        },
    },
    "oro_min": {
        "es": {
            "titulo": "Te falta oro",
            "frase": "Vas por detrás en objetos y se nota en cada pelea.",
            "consejo": "Limpia la oleada entre peleas, no dejes campamentos sin hacer y no vuelvas a base con oro sin gastar.",
        },
        "en": {
            "titulo": "You're short on gold",
            "frase": "You're behind on items and it shows in every fight.",
            "consejo": "Clear waves between fights, don't leave camps up, and don't go back to base without spending.",
        },
    },
    "mitigado": {
        "es": {
            "titulo": "Aguantas poco",
            "frase": "Mitigas menos daño que el resto: o no te pones delante o vas de papel.",
            "consejo": "Si juegas de solo o guardián, mete antes objetos defensivos y ponte delante en las peleas.",
        },
        "en": {
            "titulo": "You don't soak much",
            "frase": "You mitigate less than the rest: either you're not in front or you're made of paper.",
            "consejo": "If you play solo or guardian, buy defensive items earlier and stand in front during fights.",
        },
    },
}

# ─── Fortalezas por métrica ──────────────────────────────────────────────────

FORTALEZAS: dict[str, dict[Idioma, dict[str, str]]] = {
    "winrate": {
        "es": {"titulo": "Ganas más de lo que pierdes", "consejo": "Sigue con el mismo grupo y la misma forma de jugar: funciona."},
        "en": {"titulo": "You win more than you lose", "consejo": "Keep the same group and approach: it's working."},
    },
    "kd": {
        "es": {"titulo": "K/D por encima", "consejo": "Ahora conviértelo en rondas: pasa info y juega para el trade."},
        "en": {"titulo": "K/D above the bar", "consejo": "Now turn it into rounds: share info and play for trades."},
    },
    "adr": {
        "es": {"titulo": "Daño de sobra", "consejo": "Comunica a quién has dañado: un rival tocado es una kill fácil para otro."},
        "en": {"titulo": "Plenty of damage", "consejo": "Call out who you've damaged: a hurt enemy is an easy kill for someone else."},
    },
    "hs_pct": {
        "es": {"titulo": "Puntería de élite", "consejo": "Aprovéchala en duelos de primera bala y con la Deagle en rondas de ahorro."},
        "en": {"titulo": "Elite aim", "consejo": "Use it in first-bullet duels and with the Deagle on eco rounds."},
    },
    "kr": {
        "es": {"titulo": "Mucho impacto por ronda", "consejo": "Pide recursos al equipo: la AWP o las mejores compras te sacan partido."},
        "en": {"titulo": "High impact per round", "consejo": "Ask the team for resources: the AWP or the best buys suit you."},
    },
    "entry_pct": {
        "es": {"titulo": "Abres rondas como nadie", "consejo": "Quédate el rol de entry y pide que te sigan de cerca para el trade."},
        "en": {"titulo": "You open rounds like no one else", "consejo": "Keep the entry role and ask your team to follow closely for the trade."},
    },
    "clutch_pct": {
        "es": {"titulo": "Sangre fría en los clutch", "consejo": "Juega rotaciones tardías o el lurk: llegarás a muchos finales de ronda."},
        "en": {"titulo": "Ice-cold in clutches", "consejo": "Play late rotations or lurk: you'll end up in lots of round endings."},
    },
    "kills_media": {
        "es": {"titulo": "Kills / partida: por encima del equipo", "consejo": "Eres la fuente de daño: pide que jueguen a tu alrededor."},
        "en": {"titulo": "Kills / match: above the team", "consejo": "You're the damage source: ask the team to play around you."},
    },
    "muertes_media": {
        "es": {"titulo": "Mueres poco", "consejo": "Usa esa supervivencia para dar información hasta el final de la ronda o la pelea."},
        "en": {"titulo": "You rarely die", "consejo": "Use that survival to keep feeding info until the end of the round or fight."},
    },
    "asistencias_media": {
        "es": {"titulo": "Jugador de equipo", "consejo": "Tus asistencias sostienen al grupo: no cambies ese estilo."},
        "en": {"titulo": "Team player", "consejo": "Your assists hold the group together: keep that style."},
    },
    "dano_utilidad": {
        "es": {"titulo": "Utilidad bien usada", "consejo": "Enseña tus lineups al resto: el equipo entero ganará rondas con ellas."},
        "en": {"titulo": "Utility well used", "consejo": "Teach your lineups to the others: the whole team will win rounds with them."},
    },
    "kda": {
        "es": {"titulo": "KDA de los buenos", "consejo": "Mantén ese criterio para elegir peleas y empuja objetivos tras cada pelea ganada."},
        "en": {"titulo": "A proper KDA", "consejo": "Keep picking fights that well and push objectives after each fight you win."},
    },
    "dano_min": {
        "es": {"titulo": "Mucho daño por minuto", "consejo": "Eres el que más pega: colócate seguro en las peleas para seguir haciéndolo."},
        "en": {"titulo": "High damage per minute", "consejo": "You hit the hardest: position safely in fights to keep doing it."},
    },
    "oro_min": {
        "es": {"titulo": "Buen farmeo", "consejo": "Convierte ese oro en presión: con ventaja de objetos, fuerza peleas y objetivos."},
        "en": {"titulo": "Good farm", "consejo": "Turn that gold into pressure: with an item lead, force fights and objectives."},
    },
    "mitigado": {
        "es": {"titulo": "Aguantas lo que te echen", "consejo": "Inicia tú las peleas: el equipo puede pegar mientras tú absorbes."},
        "en": {"titulo": "You soak everything", "consejo": "Start the fights yourself: your team can hit while you absorb."},
    },
}

# ─── Reglas especiales ───────────────────────────────────────────────────────

ESPECIALES: dict[str, dict[Idioma, T]] = {
    "muestra": {
        "es": {
            "titulo": "Aún es pronto para juzgarte",
            "frase": "Solo hay {partidas} partidas de {juego}. Con eso no saco ni un mal chiste.",
            "consejo": "Juega unas cuantas más: a partir de 5 ya te digo en qué mejorar con datos de verdad.",
        },
        "en": {
            "titulo": "Too early to judge you",
            "frase": "Only {partidas} {juego} matches. Not enough for even a bad joke.",
            "consejo": "Play a few more: from 5 on I can tell you what to improve with real data.",
        },
    },
    "kd_sin_victorias": {
        "es": {
            "titulo": "Tus kills no se convierten en victorias",
            "frase": "K/D de {kd} y aun así ganas solo el {winrate} de las partidas. Las kills están; las victorias, no.",
            "consejo": {
                "cs2": "Juega más para el equipo: tradea al que entra, guarda utilidad para el retake y prioriza ganar la ronda sobre la kill.",
                "smite2": "Después de cada pelea ganada, empuja torre u objetivo. Las kills sin presión no ganan partidas.",
            },
        },
        "en": {
            "titulo": "Your kills don't turn into wins",
            "frase": "A {kd} K/D and you still only win {winrate} of your matches. The kills are there; the wins aren't.",
            "consejo": {
                "cs2": "Play more for the team: trade your entry, save utility for the retake and value the round over the kill.",
                "smite2": "After every fight you win, push a tower or an objective. Kills without pressure don't win games.",
            },
        },
    },
    "racha_mala": {
        "es": {
            "titulo": "Racha negra",
            "frase": "{derrotas} derrotas en las últimas {n} partidas. El tilt se huele desde aquí.",
            "consejo": "Para un rato. Calienta 10 minutos antes de la siguiente y no encadenes partidas enfadado.",
        },
        "en": {
            "titulo": "Losing streak",
            "frase": "{derrotas} losses in the last {n} matches. I can smell the tilt from here.",
            "consejo": "Take a break. Warm up for 10 minutes before the next one and don't chain games while angry.",
        },
    },
    "racha_buena": {
        "es": {
            "titulo": "Estás on fire",
            "frase": "{victorias} victorias en las últimas {n} partidas. Disfrútalo mientras dure.",
            "consejo": "Aprovecha la buena dinámica y juega con el mismo grupo mientras siga funcionando.",
        },
        "en": {
            "titulo": "You're on fire",
            "frase": "{victorias} wins in the last {n} matches. Enjoy it while it lasts.",
            "consejo": "Ride the momentum and keep playing with the same group while it works.",
        },
    },
    "tendencia_sube": {
        "es": {
            "titulo": "Vas a más",
            "frase": "En tus últimas {n} partidas el K/D es {reciente}, por encima de tu {global} habitual.",
            "consejo": "Lo que sea que hayas cambiado, funciona. No lo toques.",
        },
        "en": {
            "titulo": "You're trending up",
            "frase": "Over your last {n} matches your K/D is {reciente}, above your usual {global}.",
            "consejo": "Whatever you changed is working. Don't touch it.",
        },
    },
    "tendencia_baja": {
        "es": {
            "titulo": "Bajón reciente",
            "frase": "En tus últimas {n} partidas el K/D cae a {reciente}, cuando lo normal en ti es {global}.",
            "consejo": "Revisa qué ha cambiado (sensibilidad, rol, horas de sueño…) y vuelve a lo que te funcionaba.",
        },
        "en": {
            "titulo": "Recent slump",
            "frase": "Over your last {n} matches your K/D drops to {reciente}, when you usually sit at {global}.",
            "consejo": "Check what changed (sensitivity, role, sleep…) and go back to what worked.",
        },
    },
    "desglose_malo": {
        "es": {
            "titulo": "{clave} se te atraganta",
            "frase": "{victorias} de {partidas} ganadas ({winrate}). Las estadísticas no perdonan.",
            "consejo": {
                "cs2": "Banéalo si puedes, o repasad 2–3 posiciones por lado antes de volver a jugarlo.",
                "smite2": "Practícalo en partidas normales o cámbialo por otro de tu rol hasta pillarle el truco.",
            },
        },
        "en": {
            "titulo": "{clave} is your weak spot",
            "frase": "{victorias} of {partidas} won ({winrate}). Stats don't forgive.",
            "consejo": {
                "cs2": "Ban it when you can, or review 2–3 positions per side before playing it again.",
                "smite2": "Practise it in casual matches or swap it for another pick in your role until it clicks.",
            },
        },
    },
    "companero_bueno": {
        "es": {
            "titulo": "Con {nombre} vas a otro nivel",
            "frase": "Con {nombre} ganas el {con} de {partidas} partidas; sin {nombre}, el {sin}.",
            "consejo": "Buscad partidas juntos siempre que podáis: lo que hacéis funciona.",
        },
        "en": {
            "titulo": "You click with {nombre}",
            "frase": "With {nombre} you win {con} of {partidas} matches; without {nombre}, {sin}.",
            "consejo": "Queue together whenever you can: whatever you're doing works.",
        },
    },
    "companero_malo": {
        "es": {
            "titulo": "Con {nombre} no termina de cuajar",
            "frase": "Con {nombre} ganas el {con} de {partidas} partidas; sin {nombre}, el {sin}.",
            "consejo": {
                "cs2": "No es culpa de nadie en concreto: repartid roles y posiciones antes de jugar juntos y repasad qué falla en las rondas que perdéis.",
                "smite2": "Hablad la composición antes de entrar (quién inicia, quién protege) o probad otra combinación del equipo.",
            },
        },
        "en": {
            "titulo": "It's not clicking with {nombre}",
            "frase": "With {nombre} you win {con} of {partidas} matches; without {nombre}, {sin}.",
            "consejo": {
                "cs2": "It's nobody's fault in particular: split roles and positions before playing together and review the rounds you lose.",
                "smite2": "Agree on the comp before queueing (who engages, who peels) or try another team combo.",
            },
        },
    },
    "tilt_sesion": {
        "es": {
            "titulo": "Las sesiones largas se te atragantan",
            "frase": "A partir de la 3ª partida seguida ganas el {winrate} ({partidas} partidas); en las dos primeras, el {resto}.",
            "consejo": "Haz sesiones de dos o tres partidas y, si pierdes dos seguidas, para por hoy.",
        },
        "en": {
            "titulo": "Long sessions wear you down",
            "frase": "From your 3rd match in a row you win {winrate} ({partidas} matches); in the first two, {resto}.",
            "consejo": "Keep sessions to two or three matches and, if you lose two in a row, call it a day.",
        },
    },
    "tilt_derrota": {
        "es": {
            "titulo": "Una derrota te arrastra a la siguiente",
            "frase": "Después de perder, la siguiente la ganas el {winrate} de las veces ({partidas} partidas); el resto, el {resto}.",
            "consejo": "Tras una derrota, cinco minutos fuera: agua, estirar y vuelta. Y tras dos seguidas, se acabó por hoy.",
        },
        "en": {
            "titulo": "One loss drags you into the next",
            "frase": "After a loss you win the next one {winrate} of the time ({partidas} matches); otherwise, {resto}.",
            "consejo": "After a loss, take five: water, stretch, back in. After two in a row, call it a day.",
        },
    },
    "mejor_horario": {
        "es": {
            "titulo": "Rindes más {franja}",
            "frase": "{Franja} ganas el {winrate} ({partidas} partidas); el resto del día, el {resto}.",
            "consejo": "Si vas a jugar para subir, juega {franja}; a otras horas, partidas tranquilas.",
        },
        "en": {
            "titulo": "You play best {franja}",
            "frase": "{Franja} you win {winrate} ({partidas} matches); the rest of the day, {resto}.",
            "consejo": "If you're playing to climb, play {franja}; at other times, keep it casual.",
        },
    },
    "consejo_funciona": {
        "es": {
            "titulo": "Mejora en {metrica_frase}",
            "frase": "Hace {dias} días te avisé: «{aviso}». Entonces tenías {antes}; en las {partidas} partidas desde "
            "entonces, {ahora}.",
            "consejo": "Lo que estés haciendo, funciona: no lo sueltes.",
        },
        "en": {
            "titulo": "{metrica} is improving",
            "frase": "{dias} days ago I warned you: '{aviso}'. You had {antes}; over the {partidas} matches since, "
            "{ahora}.",
            "consejo": "Whatever you're doing is working: keep it up.",
        },
    },
    "consejo_no_funciona": {
        "es": {
            "titulo": "{metrica} sigue sin mejorar",
            "frase": "Hace {dias} días te avisé: «{aviso}». Entonces tenías {antes}; en las {partidas} partidas desde "
            "entonces, {ahora}.",
            # Si el aviso tenía consejo, se repite con "insiste" (FRASES); si no, este.
            "consejo": "Toca insistir o probar otra cosa: así no basta.",
        },
        "en": {
            "titulo": "{metrica} still isn't improving",
            "frase": "{dias} days ago I warned you: '{aviso}'. You had {antes}; over the {partidas} matches since, "
            "{ahora}.",
            "consejo": "Time to keep at it or try something else: this isn't enough.",
        },
    },
    "desglose_bueno": {
        "es": {
            "titulo": "{clave} es tu casa",
            "frase": "{victorias} de {partidas} ganadas ({winrate}).",
            "consejo": {
                "cs2": "Pídelo en el veto siempre que puedas.",
                "smite2": "Pídelo en la selección siempre que puedas.",
            },
        },
        "en": {
            "titulo": "{clave} is your home turf",
            "frase": "{victorias} of {partidas} won ({winrate}).",
            "consejo": {
                "cs2": "Push for it in the veto whenever you can.",
                "smite2": "Ask for it in the draft whenever you can.",
            },
        },
    },
}

# ─── Piezas sueltas ──────────────────────────────────────────────────────────

FRASES: dict[Idioma, dict[str, str]] = {
    "es": {
        "tu": "Tú",
        "equipo": "Equipo",
        "referencia": "Referencia",
        "global": "Global",
        "ultimas": "Últimas {n}",
        "con": "Con {nombre}",
        "sin": "Sin {nombre}",
        "primeras": "1ª y 2ª",
        "resto": "El resto",
        "resto_dia": "Resto del día",
        "entonces": "Entonces",
        "desde_entonces": "Desde entonces",
        "insiste": "Toca insistir: {consejo}",
        "cmp_equipo_ref": "Tienes {tu}; el resto del equipo, {equipo}, y un jugador medio anda por {ref}.",
        "cmp_equipo": "Tienes {tu}; el resto del equipo, {equipo}.",
        "cmp_ref": "Tienes {tu}; un jugador medio anda por {ref}.",
        "cmp_solo": "Tienes {tu}.",
        "nivel": "Nivel {nivel}",
        "cmp_equipo_nivel": "Tienes {tu}; el resto del equipo, {equipo}, y un jugador de nivel {nivel} de FACEIT anda "
        "por {ref}.",
        "cmp_nivel": "Tienes {tu}; un jugador de nivel {nivel} de FACEIT anda por {ref}.",
        "mejor_que": "Lo haces mejor que en el {pct} de las partidas de ese nivel.",
        "rol_pesa_debil": "Y en tu rol de {rol}, esto es lo que más cuenta.",
        "rol_pesa_fuerte": "Justo lo que pide tu rol de {rol}.",
        "rol_tolera": "En tu rol de {rol} se perdona algo, pero no tanto.",
    },
    "en": {
        "tu": "You",
        "equipo": "Team",
        "referencia": "Benchmark",
        "global": "Overall",
        "ultimas": "Last {n}",
        "con": "With {nombre}",
        "sin": "Without {nombre}",
        "primeras": "1st and 2nd",
        "resto": "The rest",
        "resto_dia": "Rest of the day",
        "entonces": "Back then",
        "desde_entonces": "Since then",
        "insiste": "Time to keep at it: {consejo}",
        "cmp_equipo_ref":"You have {tu}; the rest of the team, {equipo}, and an average player sits around {ref}.",
        "cmp_equipo": "You have {tu}; the rest of the team, {equipo}.",
        "cmp_ref": "You have {tu}; an average player sits around {ref}.",
        "cmp_solo": "You have {tu}.",
        "nivel": "Level {nivel}",
        "cmp_equipo_nivel": "You have {tu}; the rest of the team, {equipo}, and a FACEIT level {nivel} player sits "
        "around {ref}.",
        "cmp_nivel": "You have {tu}; a FACEIT level {nivel} player sits around {ref}.",
        "mejor_que": "You do better than in {pct} of the matches at that level.",
        "rol_pesa_debil": "And in your {rol} role, this is what counts most.",
        "rol_pesa_fuerte": "Exactly what your {rol} role calls for.",
        "rol_tolera": "Your {rol} role buys you some slack, but not this much.",
    },
}

# ─── Informe de cada partida (P10) ───────────────────────────────────────────
# Una frase por tipo de hecho, con variantes (se elige siempre la misma para la misma partida). Huecos: {ordinal}
# (Tercera, Cuarta...), {n}, {clave} (mapa o dios), {que} (la métrica en una frase), {metrica}, {valor}, {referencia}.

INFORMES: dict[str, dict[Idioma, list[str]]] = {
    "racha_derrotas": {
        "es": ["{ordinal} derrota seguida.", "Van {n} derrotas seguidas: igual toca parar."],
        "en": ["{ordinal} loss in a row.", "That's {n} losses in a row: maybe time for a break."],
    },
    "racha_victorias": {
        "es": ["{ordinal} victoria seguida.", "Van {n} seguidas ganando. Que no pare."],
        "en": ["{ordinal} win in a row.", "That's {n} wins in a row. Keep it going."],
    },
    "fin_racha_derrotas": {
        "es": ["Por fin: se acabó la racha de {n} derrotas.", "Se rompe la racha de {n} derrotas. Ya era hora."],
        "en": ["Finally: the {n}-loss streak is over.", "The {n}-loss streak is broken. About time."],
    },
    "fin_racha_victorias": {
        "es": ["Se acabó la racha de {n} victorias. Tenía que pasar.", "Adiós a la racha de {n} victorias."],
        "en": ["The {n}-win streak is over. It had to happen.", "Goodbye to the {n}-win streak."],
    },
    "racha_derrotas_clave": {
        "es": ["{ordinal} derrota seguida en {clave}.", "{clave} ya va por {n} derrotas seguidas: se te atraganta."],
        "en": ["{ordinal} loss in a row on {clave}.", "{clave}: {n} losses in a row. It's got your number."],
    },
    "racha_victorias_clave": {
        "es": ["{ordinal} victoria seguida en {clave}: ya es tu terreno.", "{n} seguidas ganando en {clave}."],
        "en": ["{ordinal} win in a row on {clave}: your turf now.", "{n} straight wins on {clave}."],
    },
    "estreno_clave": {
        "es": ["Estreno en {clave}.", "Primera vez en {clave}."],
        "en": ["First time on {clave}.", "{clave} debut."],
    },
    "mejor_mes": {
        "es": ["{Que_mejor} del mes: {valor} (lo mejor de antes, {referencia})."],
        "en": ["{Que_mejor} of the month: {valor} (the previous best, {referencia})."],
    },
    "peor_mes": {
        "es": ["{Que_peor} del mes: {valor} (lo peor de antes, {referencia})."],
        "en": ["{Que_peor} of the month: {valor} (the previous worst, {referencia})."],
    },
    "sobre_media": {
        "es": ["{Metrica} muy por encima de lo tuyo: {valor} (sueles andar por {referencia})."],
        "en": ["{Metrica} way above your usual: {valor} (you usually sit around {referencia})."],
    },
    "bajo_media": {
        "es": ["{Metrica} muy por debajo de lo tuyo: {valor} (sueles andar por {referencia})."],
        "en": ["{Metrica} way below your usual: {valor} (you usually sit around {referencia})."],
    },
}

# Cómo se dice cada métrica en "tu mejor ... del mes" y en "... muy por encima de lo tuyo".
METRICAS_INFORME: dict[str, dict[Idioma, dict[str, str]]] = {
    "kills_media": {
        "es": {"mejor": "tu partida con más kills", "peor": "tu partida con menos kills", "nombre": "kills"},
        "en": {"mejor": "your highest-kill game", "peor": "your lowest-kill game", "nombre": "kills"},
    },
    "adr": {
        "es": {"mejor": "tu mejor ADR", "peor": "tu peor ADR", "nombre": "ADR"},
        "en": {"mejor": "your best ADR", "peor": "your worst ADR", "nombre": "ADR"},
    },
    "hs_pct": {
        "es": {"mejor": "tu mejor % de headshot", "peor": "tu peor % de headshot", "nombre": "% de headshot"},
        "en": {"mejor": "your best headshot %", "peor": "your worst headshot %", "nombre": "headshot %"},
    },
    "kd": {
        "es": {"mejor": "tu mejor K/D", "peor": "tu peor K/D", "nombre": "K/D"},
        "en": {"mejor": "your best K/D", "peor": "your worst K/D", "nombre": "K/D"},
    },
    "kda": {
        "es": {"mejor": "tu mejor KDA", "peor": "tu peor KDA", "nombre": "KDA"},
        "en": {"mejor": "your best KDA", "peor": "your worst KDA", "nombre": "KDA"},
    },
    "dano": {
        "es": {"mejor": "tu partida con más daño", "peor": "tu partida con menos daño", "nombre": "daño"},
        "en": {"mejor": "your highest-damage game", "peor": "your lowest-damage game", "nombre": "damage"},
    },
}

# Cómo se une la segunda frase según lo que dice la primera: lo mismo (las dos buenas o las dos malas) o lo contrario.
CONECTORES: dict[Idioma, dict[str, str]] = {
    "es": {"y_encima": "Y encima, ", "al_menos": "Al menos, ", "eso_si": "Eso sí, ", "ademas": "Además, "},
    "en": {"y_encima": "And on top of that, ", "al_menos": "At least ", "eso_si": "Mind you, ", "ademas": "Also, "},
}

ORDINALES: dict[Idioma, dict[int, str]] = {
    "es": {2: "Segunda", 3: "Tercera", 4: "Cuarta", 5: "Quinta", 6: "Sexta", 7: "Séptima", 8: "Octava", 9: "Novena",
           10: "Décima"},
    "en": {2: "Second", 3: "Third", 4: "Fourth", 5: "Fifth", 6: "Sixth", 7: "Seventh", 8: "Eighth", 9: "Ninth",
           10: "Tenth"},
}

# ─── Resumen de la semana (P11) ──────────────────────────────────────────────

SEMANA: dict[Idioma, dict[str, str]] = {
    "es": {
        "juego": "**{juego}**: {partidas} {palabra_partidas} del equipo en los últimos 7 días.",
        "mejor": "El mejor, {nombre}: {victorias} de {partidas} ganadas ({winrate}).",
        "peor": "El peor, {nombre}: {victorias} de {partidas} ({winrate}).",
        "nadie": "Nadie llegó a 3 partidas: no hay mejor ni peor.",
        "solo": "Solo {nombre} llegó a 3 partidas: {victorias} de {partidas} ganadas ({winrate}).",
        "pleno": "Que alguien le pida el secreto a {nombre}.",
        "hundido": "A {nombre} le toca descanso o entrenamiento, lo que prefiera.",
        "partida": "partida",
        "partidas": "partidas",
    },
    "en": {
        "juego": "**{juego}**: {partidas} team {palabra_partidas} in the last 7 days.",
        "mejor": "Best: {nombre}, {victorias} of {partidas} won ({winrate}).",
        "peor": "Worst: {nombre}, {victorias} of {partidas} ({winrate}).",
        "nadie": "Nobody reached 3 matches: no best or worst this time.",
        "solo": "Only {nombre} reached 3 matches: {victorias} of {partidas} won ({winrate}).",
        "pleno": "Someone ask {nombre} for the secret.",
        "hundido": "{nombre} needs a rest or some practice, their pick.",
        "partida": "match",
        "partidas": "matches",
    },
}

# Nombre de cada rol para meterlo en una frase ("tu rol de {rol}" / "your {rol} role").
NOMBRES_ROL: dict[str, dict[Idioma, str]] = {
    "entry": {"es": "entry", "en": "entry"},
    "awp": {"es": "AWP", "en": "AWP"},
    "soporte": {"es": "soporte", "en": "support"},
    "lurker": {"es": "lurker", "en": "lurker"},
    "igl": {"es": "IGL", "en": "IGL"},
    "rifler": {"es": "rifler", "en": "rifler"},
    "solo": {"es": "solo", "en": "solo"},
    "jungla": {"es": "jungla", "en": "jungle"},
    "mid": {"es": "mid", "en": "mid"},
    "guardian": {"es": "guardián", "en": "guardian"},
    "carry": {"es": "carry", "en": "carry"},
}


# Cada fila de las sesiones (la clave que manda la API), como etiqueta.
NOMBRES_MOMENTO: dict[str, dict[Idioma, str]] = {
    "1": {"es": "1ª de la sesión", "en": "1st of the session"},
    "2": {"es": "2ª", "en": "2nd"},
    "3+": {"es": "3ª en adelante", "en": "3rd onwards"},
    "victoria": {"es": "Tras una victoria", "en": "After a win"},
    "derrota": {"es": "Tras una derrota", "en": "After a loss"},
    "manana": {"es": "Por la mañana", "en": "In the morning"},
    "tarde": {"es": "Por la tarde", "en": "In the afternoon"},
    "noche": {"es": "Por la noche", "en": "In the evening"},
    "madrugada": {"es": "De madrugada", "en": "Late at night"},
}


def consejo_para(entrada: T, juego: Juego) -> str | None:
    """El consejo de una entrada, eligiendo el del juego si depende de él."""
    consejo = entrada.get("consejo")
    if isinstance(consejo, dict):
        return consejo.get(juego)
    return consejo
