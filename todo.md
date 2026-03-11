# To-Do List: Blackjack funzionante base ma completo

## Contesto

Il progetto ha 27 issues su GitHub divise in 3 aree:
- **Gameplay** (#25 parent): #1-#13 — regole del blackjack, multiplayer locale, puntate, azioni
- **Persistenza** (#26 parent): #14-#19 — salvataggio/caricamento stato di gioco
- **Licenza** (#27 parent): #20-#24 — modulo C per validazione licenza

Molte issues sono "da chiarire" col committente. Per una versione **base ma completa** assumo decisioni ragionevoli dove serve (segnalate con *).

La struttura delle cartelle e' gia' creata. Tutti i file Java sono vuoti. File esistenti dal prof: FileService.java, MainApp.java, Controller.java, main.fxml, pom.xml.

---

## FASE 1 — Modello di dominio (Backend model)

### 1.1 Card.java (#2, #10)
- [ ] Enum `Suit` (HEARTS, DIAMONDS, CLUBS, SPADES) (#2)
- [ ] Enum `Rank` (ACE, TWO, ..., KING) con valore numerico base (#2)
- [ ] Metodo `getValue()` che ritorna il valore (ACE=11, figure=10) (#10)
- [ ] `toString()` per debug

### 1.2 Deck.java (#2)
- [ ] Costruttore che crea 6 mazzi da 52 carte = 312 carte (#2)
- [ ] `shuffle()` con algoritmo Fisher-Yates (uniforme e riproducibile con seed) (#2)
- [ ] `draw()` pesca una carta dalla cima (#2)
- [ ] `remainingCards()` per sapere quante ne restano (#2)
- [ ] `needsReshuffle()` ritorna true alla soglia di penetrazione (~75%) (#2)
- [ ] `reset()` ricrea e rimescola il mazzo (#2)

### 1.3 Hand.java (#10)
- [ ] `addCard(Card)` aggiunge carta alla mano (#10)
- [ ] `getScore()` calcola il punteggio gestendo assi dinamicamente (#10):
  - Asso vale 11, se sfora 21 diventa 1
  - Gestisce piu' assi nella stessa mano
- [ ] `isBusted()` → score > 21 (#10)
- [ ] `isBlackjack()` → 2 carte con score == 21 (#10)
- [ ] `isSoft()` → ha un asso contato come 11 (#10)
- [ ] `getCards()` ritorna lista carte (#10)
- [ ] `clear()` svuota la mano (#10)

### 1.4 Player.java (#1, #11)
- [ ] Nome, saldo chips (balance), mano corrente (Hand) (#1)
- [ ] `placeBet(amount)` con validazione (>= minimo tavolo, <= balance) (#11)
- [ ] `win(amount)`, `lose()`, `push()` (pareggio) per aggiornare il saldo (#11)
- [ ] `getBalance()`, `getName()`, `getHand()` (#1)

### 1.5 Dealer.java (#3)
- [ ] Estende o compone Hand (#3)
- [ ] `shouldHit()` → *banco sta su hard 17, pesca su soft 17* (#3 - decisione assunta)
- [ ] `getVisibleCard()` → la prima carta (quella scoperta) (#3)
- [ ] `isHandRevealed()` → flag per UI (carte coperte/scoperte) (#3)

---

## FASE 2 — Logica di gioco (Backend game)

### 2.1 GameState.java (#25)
- [ ] Enum: `WAITING`, `BETTING`, `DEALING`, `PLAYER_TURN`, `DEALER_TURN`, `RESOLVING`, `ROUND_OVER` (#25)

### 2.2 GameManager.java (#25, #1, #2, #3, #4, #12)
- [ ] Gestione lista giocatori (1+ giocatori locali) (#1)
- [ ] Indice del giocatore corrente (turni sequenziali) (#1)
- [ ] Stato di gioco corrente (GameState) (#25)
- [ ] Riferimento al Deck e al Dealer (#2, #3)
- [ ] **Flusso di gioco:**
  - [ ] `startNewRound()` → reset mani, stato a BETTING (#25)
  - [ ] `placeBet(playerIndex, amount)` → valida e registra puntata (#11)
  - [ ] `deal()` → distribuisce 2 carte a ogni giocatore + 2 al banco, stato a PLAYER_TURN (#25)
  - [ ] `hit()` → pesca carta per giocatore corrente, se busta passa al prossimo (#25)
  - [ ] `stand()` → passa al giocatore/banco successivo (#25)
  - [ ] `dealerPlay()` → banco gioca automaticamente secondo regole (#3)
  - [ ] `resolveRound()` → determina vincitori, aggiorna saldi (#4)
- [ ] **Regole payout:**
  - [ ] *Blackjack naturale paga 3:2* (#4 - decisione assunta)
  - [ ] Vittoria normale paga 1:1 (#4)
  - [ ] Push (pareggio) → restituzione puntata (#4)
- [ ] `getCurrentPlayer()`, `getState()`, `getDealer()` (#25)
- [ ] Gestione reshuffle quando il mazzo e' sotto soglia (#2)
- [ ] Supporto sessioni multi-mano senza riavvio (#12)

### 2.3 Azioni avanzate (opzionali per v1, ma struttura pronta)
- [ ] *Niente split, double down, assicurazione nella v1* (#5, #6, #7, #8, #9 — tutti "da chiarire")
- [ ] Predisporre metodi stub: `doubleDown()` (#7), `split()` (#6), `insurance()` (#5)

---

## FASE 3 — Interfaccia grafica (Frontend)

### 3.1 Ristrutturare MainApp.java (#23, #27)
- [ ] Al lancio: verifica licenza (LicenseService), se fallisce mostra errore e chiude (#23)
- [ ] Se licenza OK: carica la schermata di gioco (game.fxml) (#27)
- [ ] Gestione i18n: caricamento ResourceBundle in base alla lingua

### 3.2 game.fxml (#25)
- [ ] Area carte del banco (in alto) (#25)
- [ ] Area carte del giocatore (in basso) (#25)
- [ ] Label punteggio banco e giocatore (#25)
- [ ] Label saldo e puntata corrente (#11)
- [ ] Bottoni azioni: Hit, Stand, Nuova Mano, Salva, Carica (#25, #26)
- [ ] Label stato/messaggi (es. "Hai vinto!", "Banco vince", "Pareggio") (#25)
- [ ] Menu in alto: File (Salva/Carica/Esci), Lingua (IT/EN) (#26)

### 3.3 GameController.java (#25)
- [ ] Riferimento a GameManager (backend) (#25)
- [ ] `@FXML` binding di tutti gli elementi UI (#25)
- [ ] `onHitClicked()` → chiama gameManager.hit(), aggiorna UI (#25)
- [ ] `onStandClicked()` → chiama gameManager.stand(), aggiorna UI (#25)
- [ ] `onNewRoundClicked()` → avvia nuovo round, reset UI (#12)
- [ ] `onSaveClicked()` → chiama PersistenceService.save() (#14)
- [ ] `onLoadClicked()` → chiama PersistenceService.load(), aggiorna UI (#14)
- [ ] `updateUI()` → metodo centrale che sincronizza tutta la UI con lo stato del GameManager (#25)
- [ ] Gestione abilitazione/disabilitazione bottoni in base allo stato di gioco (#25)
- [ ] Supporto selezione puntata (TextField o Spinner) prima del deal (#11)

### 3.4 CardView.java (#25)
- [ ] Componente per mostrare una carta (testo o immagine) (#25)
- [ ] Supporto carta coperta (per la carta nascosta del banco) (#25)
- [ ] *Per la v1: rappresentazione testuale (es. "A♠", "K♥")* — immagini dopo

### 3.5 MenuController.java (#26)
- [ ] Gestione menu File: Nuova Partita, Salva, Carica, Esci (#26)
- [ ] Gestione menu Lingua: cambio IT/EN a runtime

### 3.6 Rimuovere/refactorare Controller.java del prof
- [ ] Il Controller.java originale (textarea + save) va sostituito/rimosso
- [ ] La logica di save del prof migra in PersistenceService (#14)

---

## FASE 4 — Persistenza (#26)

### 4.1 PersistenceService.java (#14, #15, #18)
- [ ] `save(GameManager, Path)` → serializza stato partita su file (#14)
- [ ] `load(Path)` → deserializza e ricostruisce GameManager (#14)
- [ ] *Formato JSON* (#18 - decisione assunta, leggibile e standard)
- [ ] Usa FileService.java gia' esistente per la scrittura su disco
- [ ] Salva: stato gioco, carte in mano, saldi, puntate, mazzo rimanente (#16)
- [ ] Salvataggio esplicito manuale, niente auto-save (#14)
- [ ] Conferma salvataggio all'utente tramite UI (#14)

### 4.2 Struttura dati salvataggio (#16, #17)
- [ ] Classe `GameSaveData` (DTO serializzabile) con:
  - Stato corrente (GameState) (#16)
  - Lista giocatori (nome, saldo, carte in mano, puntata) (#16)
  - Carte del banco (#16)
  - Carte rimanenti nel mazzo (#16)
  - Timestamp salvataggio (#17)

---

## FASE 5 — Modulo Licenza in C (#27)

### 5.1 license_checker.c (#20, #21, #22, #24)
- [ ] Legge un file `license.key` dalla stessa directory (#20)
- [ ] *Validazione semplice: controlla che il file esista e contenga una stringa valida* (#21)
- [ ] Exit code 0 = licenza valida, exit code 1 = licenza non valida (#22)
- [ ] Stampa su stdout un messaggio di conferma/errore (#22)

### 5.2 Makefile (#24)
- [ ] Target `all`: compila `license_checker.c` → `license_checker` (eseguibile) (#24)
- [ ] Target `clean`: rimuove l'eseguibile (#24)

### 5.3 license.key (#20)
- [ ] File di esempio con chiave di licenza valida (#20)

### 5.4 LicenseService.java (#23, #24)
- [ ] Esegue `license_checker` via `ProcessBuilder` (#24)
- [ ] Interpreta exit code (0 = OK, altro = non valida) (#22)
- [ ] Metodo `isLicenseValid()` → boolean (#23)
- [ ] Nessuna logica di validazione in Java (tutta nel modulo C) (#24)

---

## FASE 6 — Internazionalizzazione (i18n)

### 6.1 MessageService.java
- [ ] Carica ResourceBundle (`messages_it.properties` o `messages_en.properties`)
- [ ] `getMessage(String key)` → ritorna la stringa tradotta
- [ ] `setLocale(Locale)` → cambia lingua a runtime
- [ ] `getLocale()` → lingua corrente

### 6.2 File .properties
- [ ] `messages_it.properties`: tutte le stringhe UI in italiano
- [ ] `messages_en.properties`: tutte le stringhe UI in inglese
- [ ] Chiavi: `game.hit`, `game.stand`, `game.newRound`, `game.bet`, `game.save`, `game.load`, `game.balance`, `game.score`, `game.dealer`, `game.player`, `game.bust`, `game.blackjack`, `game.win`, `game.lose`, `game.push`, `menu.file`, `menu.language`, `license.invalid`, ecc.

---

## FASE 7 — Test

### 7.1 CardTest.java (#2, #10)
- [ ] Test valori carte (ACE=11, KING=10, ecc.) (#10)
- [ ] Test tutti i semi (#2)

### 7.2 DeckTest.java (#2)
- [ ] Test dimensione mazzo (312 carte) (#2)
- [ ] Test shuffle (ordine diverso) (#2)
- [ ] Test draw (riduce il conteggio) (#2)
- [ ] Test reshuffle threshold (#2)

### 7.3 HandTest.java (#10)
- [ ] Test punteggio base (#10)
- [ ] Test gestione asso singolo (11 o 1) (#10)
- [ ] Test gestione assi multipli (#10)
- [ ] Test isBlackjack, isBusted, isSoft (#10)

### 7.4 GameManagerTest.java (#25)
- [ ] Test flusso completo: bet → deal → hit/stand → resolve (#25)
- [ ] Test blackjack naturale (#4)
- [ ] Test bust del giocatore (#25)
- [ ] Test turno del banco (#3)

---

## FASE 8 — Configurazione Maven

### 8.1 backend/pom.xml
- [ ] Aggiungere dipendenza per JSON (es. Gson o Jackson) per la persistenza (#18)
- [ ] Aggiungere dipendenza JUnit 5 per i test

### 8.2 frontend/pom.xml
- [ ] Verificare che le dipendenze JavaFX siano complete (controls, fxml)

---

## Ordine di implementazione consigliato

1. **Card + Deck + Hand** (#2, #10) + relativi test
2. **Player + Dealer** (#1, #3, #11)
3. **GameState + GameManager** (#25, #4, #12) + test
4. **game.fxml + GameController** (#25) — UI base per giocare
5. **license_checker.c + Makefile + LicenseService** (#20-#24, #27)
6. **MessageService + file .properties** — i18n
7. **PersistenceService + GameSaveData** (#14-#18, #26)
8. **MenuController** (#26) — menu file/lingua
9. **Rifinitura UI e integrazione finale**

---

## Decisioni assunte (da validare col committente)

| Issue | Decisione assunta | Motivo |
|-------|-------------------|--------|
| #3 Soft 17 | Banco pesca su soft 17 | Regola standard Las Vegas |
| #4 Payout BJ | 3:2 | Standard classico |
| #5 Assicurazione | Non in v1 | Da chiarire |
| #6 Split | Non in v1 | Da chiarire |
| #7 Double Down | Non in v1 | Da chiarire |
| #8 Double after Split | Non in v1 | Da chiarire |
| #9 Resplit Assi | Non in v1 | Da chiarire |
| #11 Limite max puntata | No limite max, solo minimo | Da chiarire |
| #18 Formato salvataggio | JSON | Leggibile, standard |
| #20 Fornitura licenza | File `license.key` locale | Semplice |
| #21 Tipo licenza | Stringa chiave statica | Semplice per v1 |

---

## Verifica end-to-end

1. `cd license && make && echo "VALID-KEY-2026" > license.key && ./license_checker` → exit 0
2. `mvn clean package` → build senza errori
3. `mvn -pl frontend javafx:run` → app si avvia, verifica licenza, mostra tavolo
4. Piazzare puntata → Deal → Hit/Stand → Risultato round → Nuova mano
5. Salvare partita → Chiudere → Riaprire → Caricare → Stato ripristinato
6. Cambiare lingua IT↔EN → tutte le stringhe si aggiornano
