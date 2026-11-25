# Grafik pracy

Grafik pracy to polska aplikacja na Androida 8.0 i nowszego do planowania zmian, dyżurów i dni wolnych. Wyświetla grafik tygodnia i miesiąca, podsumowuje godziny, ustawia budziki i pozwala zapisać zmiany w Kalendarzu Google.

## Instalacja

Pobierz APK z zakładki **Releases** i otwórz plik na telefonie. Android może poprosić o zezwolenie na instalację z aplikacji, w której otwierasz pobrany plik. Numer wersji znajdziesz w **Ustawienia → O aplikacji**.

## Funkcje

- Dotknij dnia i wybierz **Dodaj zmianę** albo użyj **+** u góry. Dotknij godzin zapisanej zmiany, aby ją edytować lub usunąć. Godzina końca wcześniejsza od początku oznacza zmianę nocną.
- Wybierz **☰ → Zaplanuj tydzień**, ustaw wspólne godziny i zaznacz dni pracy. Dotknij godzin przy konkretnym dniu, aby ustawić indywidualne godziny. **Kopiuj poprzedni tydzień** przenosi godziny i budziki do pustych dni.
- Początek i długość zmiany wyznaczają godzinę zakończenia. Możesz również dotknąć pola **Koniec** i ustawić godzinę ręcznie. **+1** oznacza koniec następnego dnia.
- Przeciągnij tydzień palcem, żeby podejrzeć sąsiedni. Puść po dłuższym przesunięciu, żeby zmienić tydzień, albo użyj strzałek. Przycisk **Dziś** na górze wraca do bieżącego tygodnia. Menu **☰** zawiera pełnoekranowy **Widok miesiąca** z numerami tygodni ISO. Dotknij dnia, aby otworzyć jego tydzień.
- Dotknij dnia i wybierz **Oznacz dzień**, aby wpisać urlop, L4, dzień wolny albo wolne ustawowe. W **Oznaczeniach tygodnia** ustawisz oznaczenie dla kilku dni jednocześnie i zaznaczysz dyżur od poniedziałku do niedzieli. Oznaczenie dnia jako wolnego wstrzymuje przypisane zmiany. Przywrócisz je, wybierając **Bez oznaczenia**.
- W **☰ → Godziny i urlop** znajdziesz sumy tygodniowe, miesięczne i roczne oraz ustawisz roczną pulę urlopu. Każdy dzień oznaczony jako urlop odejmuje jeden dzień od puli. Godziny zmian są liczone według daty ich rozpoczęcia, a dyżury nocne jako liczba nocy.
- Wybierz **☰ → Udostępnij tydzień** albo **Udostępnij** w widoku miesiąca, żeby otworzyć PDF w menu udostępniania Androida. Powody dni wolnych możesz dołączyć opcjonalnie.

- Budzik domyślnie dzwoni 60 minut przed zmianą. W Ustawieniach zmienisz domyślny czas dla nowych zmian. Każda zmiana może mieć własne wyprzedzenie lub wyłączony budzik.
- Jeśli aplikacja poprosi o dostęp, wybierz **☰ → Włącz dokładne budziki**. Powiadomienia udostępniają przycisk wyłączenia budzika, a uprawnienie do pełnego ekranu pozwala wyświetlać ekran budzika na blokadzie. Dźwięk korzysta z systemowej głośności alarmów.
- Włącz **Powiadomienia o zmianach** w Ustawieniach, aby dostawać osobne przypomnienie na telefonie. Domyślne wyprzedzenie to 120 minut. Powiadomienie pokazuje datę i godziny zmiany. Możesz otworzyć z niego grafik lub przełożyć przypomnienie o 15 minut, jeśli do zmiany zostało więcej czasu. **Ustawienia → Kategorie powiadomień** pokazują osobno **Przypomnienia o zmianach** i **Budzik przed pracą**. Dotknij pozycji, aby zmienić ustawienia tej kategorii w Androidzie.
- Wybierz **Ustawienia → Połącz kalendarz Google** i wskaż kalendarz synchronizowany z kontem Google w telefonie. Aplikacja zapisuje w nim wydarzenia „Praca”, a synchronizacją z kontem zajmuje się Android. Grafik pozostaje dostępny lokalnie także bez konta. Przy synchronizacji aplikacja sprawdza, czy w wybranym kalendarzu istnieje już wpis „Praca” o tych samych godzinach.

## Kopia zapasowa Androida

Systemowa kopia obejmuje zapisany grafik, dyżury, oznaczenia urlopu i L4, pule urlopu oraz ustawienia budzików i przypomnień. Włącz tworzenie kopii w ustawieniach telefonu, zwykle w **Google → Kopia zapasowa**. Android decyduje, kiedy utworzy kopię i przywróci ją przy instalacji na urządzeniu z tym samym kontem Google. Jeśli telefon udostępnia **Utwórz kopię teraz**, możesz uruchomić tworzenie kopii z jego ustawień.

Po odzyskaniu grafiku otwórz aplikację. Zaplanowane budziki i przypomnienia zostaną ustawione ponownie zgodnie z uprawnieniami telefonu. Wybierz ponownie kalendarz Google, jeśli chcesz synchronizować wydarzenia. Pliki PDF wcześniej udostępnione z aplikacji są przechowywane przez aplikację, do której je wysłano lub zapisano.

## Budowanie

Otwórz główny katalog projektu w Android Studio. Zainstaluj **Android SDK Platform 35**, skonfiguruj **JDK 17** i zsynchronizuj projekt z Gradle. Repozytorium zawiera Gradle Wrapper. Do pierwszej kompilacji potrzebujesz internetu, żeby pobrać Gradle i zależności.

W PowerShell na Windowsie uruchom:

```powershell
.\gradlew.bat assembleDebug
```

W macOS lub Linuksie uruchom:

```sh
./gradlew assembleDebug
```

Wynik znajdziesz w `app/build/outputs/apk/debug/app-debug.apk`. Aplikację możesz też uruchomić na emulatorze lub telefonie z Android Studio. Do zbudowania wariantu wydania użyj `assembleRelease`, a następnie podpisz APK własnym kluczem, jeśli dystrybuujesz własną kopię. Android wymaga tego samego podpisu przy instalowaniu aktualizacji na telefonie.

## Wydania przez GitHub Actions

W **Settings → Secrets and variables → Actions** dodaj sekrety repozytorium używane do podpisania APK. Użyj tego samego klucza co przy poprzednim wydaniu, jeśli nowy plik ma działać jako aktualizacja.

| Sekret | Wartość |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Zawartość pliku kluczy JKS lub PKCS12 zakodowana w Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Hasło do pliku kluczy |
| `ANDROID_KEY_ALIAS` | Alias klucza podpisującego |
| `ANDROID_KEY_PASSWORD` | Hasło klucza podpisującego |

Przed wydaniem ustaw `versionName` w `app/build.gradle` na nową wersję i zwiększ `versionCode`. Po wypchnięciu commita wypchnij odpowiadający mu tag, np. `v1.0.1`. Workflow **Wydanie Androida** zbuduje APK z tego tagu, podpisze je i opublikuje wydanie po dodaniu pliku APK.

Dla istniejącego tagu i nieopublikowanego wydania wybierz **Actions → Wydanie Androida → Run workflow**, a następnie wpisz tag. Workflow użyje istniejącego szkicu wydania, jeśli jest dostępny.

## Licencja

Kod aplikacji jest dostępny na licencji [MIT](LICENSE). Pliki Gradle Wrapper podlegają licencji [Apache 2.0](gradle/wrapper/LICENSE).
