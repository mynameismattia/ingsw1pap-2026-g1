<div align="center">

# ♠️ JUST21

**Il classico gioco di carte, reinventato in Java.**

![Java 21](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue?style=for-the-badge)
![Maven](https://img.shields.io/badge/Maven-Multi--Module-red?style=for-the-badge&logo=apachemaven)
![License](https://img.shields.io/badge/Licenza-Validazione_C_(JNI)-green?style=for-the-badge)

*Blackjack multiplayer locale, regole complete, interfaccia JavaFX moderna.*

</div>

---

## Funzionalità

| Funzionalità | Dettaglio |
|---|---|
| **Hit / Stand** | Le azioni base, il cuore del Blackjack |
| **Split** | Fino a 4 mani, con resplit degli assi |
| **Double Down** | Su qualsiasi mano, anche dopo lo split |
| **Assicurazione** | Scommessa laterale 2:1 con regole classiche |
| **Puntate** | Da 5 a 1.000 chips, payout Blackjack naturale 3:2 |
| **Multiplayer locale** | Fino a 4 giocatori (umani e bot), turni sequenziali |
| **Avversari CPU** | Bot con strategia automatica al tavolo |
| **Salvataggio** | Autosave a fine round + 3 slot manuali (JSON) |
| **Statistiche** | Storico mani, percentuale di vittoria, andamento del saldo |
| **Lingue** | Italiano / Inglese, cambio a runtime |
| **Audio** | Effetti e musica di sottofondo, con mixer nelle impostazioni |
| **Licenza** | Validazione tramite modulo nativo in C (JNI) |

---

## Tech Stack

```
┌─────────────────────────────────────────────┐
│              Frontend (JavaFX 21)            │
│        Scene FXML · Controller · CSS         │
├─────────────────────────────────────────────┤
│              Backend (Java 21)               │
│      Game logic · Model · i18n · Service     │
├──────────────────────┬──────────────────────┤
│     Persistenza      │     Modulo licenza    │
│   JSON (Jackson)     │      C · JNI          │
└──────────────────────┴──────────────────────┘
```

Progetto Maven multi-modulo (`backend` + `frontend`). Il modulo licenza è una
libreria nativa scritta in C, invocata da Java via JNI: viene compilata da
Maven con `gcc` durante la build e caricata a runtime tramite
`System.loadLibrary`.

---

## Requisiti

| | Versione | Note |
|---|---|---|
| **JDK** | 21 | Necessario per compilare ed eseguire |
| **Maven** | 3.9+ | Oppure il Maven incluso in IntelliJ IDEA |
| **gcc** | qualsiasi recente | Per compilare il modulo licenza nativo |
| **`JAVA_HOME`** | → JDK 21 | **Obbligatorio**: serve a `gcc` per trovare `jni.h` |

> ⚠️ Senza `JAVA_HOME` impostato la build fallisce con
> `fatal error: jni.h: No such file or directory`, perché il compilatore C
> non trova gli header JNI del JDK.

---

## Installazione

```bash
# 1. Clona il repository
git clone https://github.com/mynameismattia/ingsw1pap-2026-g1.git
cd ingsw1pap-2026-g1

# 2. Imposta JAVA_HOME sul tuo JDK 21 (adatta il percorso al tuo sistema)
export JAVA_HOME=/usr/lib/jvm/default        # Linux
# export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
# set JAVA_HOME=C:\Program Files\Java\jdk-21          # Windows (cmd)

# 3. Build completa: compila il codice Java, costruisce la libreria
#    nativa C, esegue i test e installa il modulo backend nel
#    repository Maven locale (necessario al passo successivo).
mvn clean install

# 4. Avvia il gioco
mvn -pl frontend javafx:run
```

### Cosa succede dietro le quinte

- `mvn clean install` compila `gcc` la libreria nativa in
  `backend/target/` con il nome corretto per il tuo sistema operativo
  (`liblicensechecker.so` su Linux, `.dll` su Windows, `.dylib` su macOS).
  Il sistema operativo viene rilevato automaticamente dai profili Maven.
- Usa `install` (non solo `package`): il modulo `backend` deve trovarsi nel
  repository locale perché `javafx:run` del frontend possa risolverlo.
- `mvn -pl frontend javafx:run` imposta automaticamente
  `-Djava.library.path=backend/target`, così la libreria nativa viene
  trovata e la validazione della licenza funziona.

### Chiave di licenza

Al primo avvio l'app chiede una chiave di licenza nel formato
`XXXXX-XXXXX-XXXXX-XXXXX`. Per provare il gioco puoi usare la chiave demo:

```
FELIC-EMATT-IA000-00000
```

Spuntando **"Ricordami"** la chiave viene salvata in `~/.blackjack/license`
e non verrà più richiesta agli avvii successivi.

---

## Test

I test (JUnit 5) coprono il modulo `backend` — modello, motore di gioco,
persistenza e i18n:

```bash
mvn -pl backend test
```

I test vengono eseguiti anche durante `mvn clean install`.

---

## Struttura del progetto

```
ingsw1pap-2026-g1/
├── backend/                          # Logica, modello, persistenza, licenza
│   ├── src/main/java/.../model       # Card, Deck, Hand, strategie del dealer
│   ├── src/main/java/.../game        # Motore di gioco, regole, turni
│   ├── src/main/java/.../service     # Persistenza JSON, slot di salvataggio
│   ├── src/main/java/.../i18n        # MessageService (IT / EN)
│   ├── src/main/java/.../license     # LicenseChecker (wrapper JNI)
│   └── src/main/c/                   # LicenseChecker.c — modulo nativo
├── frontend/                         # Interfaccia JavaFX
│   ├── src/main/java/.../MainApp     # Punto di ingresso
│   ├── src/main/java/.../controller  # Controller delle scene
│   ├── src/main/java/.../service     # SoundManager
│   └── src/main/resources/           # FXML, CSS, audio, font, bundle i18n
└── pom.xml                           # Reactor multi-modulo
```

---

## Team

| Nome | Ruolo |
|---|---|
| [@mynameismattia](https://github.com/mynameismattia) — Mattia Alongi | Game Logic & Architecture |
| [@FeliceRossetti](https://github.com/FeliceRossetti) — Felice Rossetti | License Module & Domain Model |

<div align="center">

*Progetto per Ingegneria e Sviluppo Software 1 — SUPSI DTI*

</div>
