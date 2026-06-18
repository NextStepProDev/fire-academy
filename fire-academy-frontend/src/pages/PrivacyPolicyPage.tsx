import { Link } from 'react-router-dom'
import { Seo } from '../components/seo/Seo'

const LAST_UPDATED = '18 czerwca 2026'

export function PrivacyPolicyPage() {
  return (
    <div className="min-h-screen bg-surface-950">
      <Seo
        title="Polityka prywatności"
        description="Polityka prywatności Fire Academy — jakie dane zbieramy, w jakim celu, jak długo je przechowujemy i jakie prawa Ci przysługują."
        path="/polityka-prywatnosci"
      />
      <div className="relative overflow-hidden bg-gradient-to-b from-surface-900 to-surface-950 border-b border-surface-800">
        <div className="absolute inset-0 opacity-5">
          <div className="absolute top-0 left-1/4 w-96 h-96 bg-primary-500 rounded-full blur-3xl" />
          <div className="absolute bottom-0 right-1/4 w-64 h-64 bg-primary-700 rounded-full blur-3xl" />
        </div>
        <div className="relative max-w-4xl mx-auto px-4 py-16 sm:py-24 text-center">
          <h1 className="text-3xl sm:text-4xl font-bold text-surface-100 mb-3">
            Polityka prywatności
          </h1>
          <p className="text-surface-400 text-lg max-w-xl mx-auto">
            Transparentność i bezpieczeństwo Twoich danych to nasz priorytet.
          </p>
        </div>
      </div>

      <div className="max-w-4xl mx-auto px-4 py-12 sm:py-16 space-y-6">

        <div className="bg-surface-900 border border-surface-800 rounded-2xl p-6 sm:p-8">
          <p className="text-surface-300 leading-relaxed">
            Dbamy o Twoje dane osobowe i zawsze będziemy dokładać wszelkich starań, aby należycie je chronić.
            Niniejsza polityka prywatności wyjaśnia, jakie dane zbieramy, w jakim celu i na jakiej podstawie prawnej,
            jak długo je przechowujemy oraz jakie prawa Ci przysługują. Napisana jest w sposób prosty i zrozumiały —
            bez zbędnego żargonu prawniczego.
          </p>
          <p className="text-surface-500 text-sm mt-4">
            Ostatnia aktualizacja: {LAST_UPDATED}
          </p>
        </div>

        <Section title="1. Administrator danych osobowych">
          <p className="text-surface-300 leading-relaxed">
            Administratorem Twoich danych osobowych jest:
          </p>
          <div className="mt-4 bg-surface-800/50 rounded-xl p-4 space-y-1 text-surface-300 text-sm">
            <p className="font-semibold text-surface-200">FIZJO4LIFE Sp. z o.o.</p>
            <p>ul. 1 Maja 5C, 32-590 Libiąż</p>
            <p>KRS: 0001024771 · NIP: 6282290548 · REGON: 524728084</p>
            <p>
              E-mail:{' '}
              <a href="mailto:fireacademy.biz@gmail.com" className="text-primary-400 hover:text-primary-300 transition-colors">
                fireacademy.biz@gmail.com
              </a>
            </p>
            <p>
              Telefon:{' '}
              <a href="tel:+48534823667" className="text-primary-400 hover:text-primary-300 transition-colors">
                +48 534 823 667
              </a>
            </p>
          </div>
          <p className="text-surface-400 text-sm mt-4 leading-relaxed">
            W sprawach dotyczących danych osobowych możesz kontaktować się z nami pod powyższym adresem e-mail.
            Staramy się odpowiadać na wszystkie wiadomości w ciągu 72 godzin.
          </p>
        </Section>

        <Section title="2. Jakie dane zbieramy">
          <p className="text-surface-400 leading-relaxed mb-4">
            Zbieramy wyłącznie dane niezbędne do założenia konta i świadczenia usług szkoleniowych. Nie zbieramy nic ponad to.
          </p>

          <SubSection title="Konto użytkownika (rejestracja)">
            <p className="text-surface-400 text-sm mb-2 leading-relaxed">
              Założenie konta jest wymagane, aby zapisać się na trening, obóz lub szkolenie. Przy rejestracji zbieramy:
            </p>
            <DataList items={[
              'Imię i nazwisko',
              'Adres e-mail',
              'Numer telefonu (opcjonalny przy rejestracji; wymagany do zapisu na wydarzenie)',
              'Hasło — przechowywane wyłącznie w postaci zaszyfrowanego hashu (bcrypt), nigdy w formie jawnej',
              'Data i godzina akceptacji polityki prywatności — jako potwierdzenie udzielonej zgody',
            ]} />
          </SubSection>

          <SubSection title="Profil użytkownika (opcjonalnie)">
            <p className="text-surface-400 text-sm mb-2 leading-relaxed">
              W ustawieniach konta możesz dobrowolnie uzupełnić:
            </p>
            <DataList items={[
              'Zdjęcie profilowe (avatar) — jeśli zdecydujesz się je dodać; w każdej chwili możesz je usunąć',
              'Preferencje powiadomień e-mail',
            ]} />
          </SubSection>

          <SubSection title="Zapis na wydarzenie">
            <p className="text-surface-400 text-sm mb-2 leading-relaxed">
              Zapis odbywa się z poziomu zalogowanego konta. Do listy uczestników trafiają Twoje imię i nazwisko,
              adres e-mail oraz numer telefonu (pobierane z profilu), a dodatkowo:
            </p>
            <DataList items={[
              'Informacja dla organizatora (opcjonalnie) — np. uwagi zdrowotne, poziom zaawansowania',
              'Data i godzina zapisu',
            ]} />
            <p className="text-surface-500 text-sm mt-3 leading-relaxed">
              Dane na liście uczestników zapisywane są jako kopia (snapshot) z chwili zapisu — dzięki temu organizator
              ma czytelny wykaz uczestników wydarzenia nawet po usunięciu lub anonimizacji Twojego konta.
            </p>
          </SubSection>

          <p className="text-surface-500 text-sm mt-4">
            Nie korzystamy z plików cookies śledzących, Google Analytics, Facebook Pixel ani żadnych innych narzędzi analitycznych.
          </p>
        </Section>

        <Section title="3. Cel i podstawa prawna przetwarzania">
          <div className="space-y-4">
            <LegalBasis
              purpose="Realizacja zapisu na wydarzenie (trening, obóz, szkolenie)"
              basis="Art. 6 ust. 1 lit. b RODO — przetwarzanie niezbędne do wykonania umowy (świadczenie usług szkoleniowych)"
            />
            <LegalBasis
              purpose="Kontakt w sprawie organizacji wydarzenia"
              basis="Art. 6 ust. 1 lit. b RODO — wykonanie umowy (potwierdzenie zapisu, zmiany w harmonogramie, informacje organizacyjne)"
            />
            <LegalBasis
              purpose="Obsługa konta użytkownika"
              basis="Art. 6 ust. 1 lit. b RODO — wykonanie umowy"
            />
            <LegalBasis
              purpose="Zdjęcie profilowe (avatar)"
              basis="Art. 6 ust. 1 lit. a RODO — dobrowolna zgoda, którą możesz w każdej chwili wycofać, usuwając zdjęcie w ustawieniach konta"
            />
            <LegalBasis
              purpose="Weryfikacja adresu e-mail i odzyskiwanie hasła"
              basis="Art. 6 ust. 1 lit. b RODO — wykonanie umowy"
            />
            <LegalBasis
              purpose="Archiwum uczestników wydarzeń (historia Twoich zapisów w koncie)"
              basis="Art. 6 ust. 1 lit. f RODO — uzasadniony interes administratora (prowadzenie dokumentacji uczestników, rozliczenia, ochrona roszczeń); dane przechowywane tak długo, jak istnieje Twoje konto"
            />
            <LegalBasis
              purpose="Bezpieczeństwo systemu (blokada konta po nieudanych logowaniach, rate limiting)"
              basis="Art. 6 ust. 1 lit. f RODO — uzasadniony interes administratora (ochrona przed nieuprawnionym dostępem)"
            />
          </div>
        </Section>

        <Section title="4. Jak długo przechowujemy dane">
          <div className="space-y-3 text-surface-300 leading-relaxed">
            <p>
              <span className="text-surface-200 font-medium">Dane konta i historia zapisów</span> — przechowywane przez
              cały czas istnienia Twojego konta. Dopóki masz konto, Twój profil oraz archiwum zapisów na wydarzenia
              pozostają dostępne (m.in. po to, byś sam widział swoją historię, a organizator mógł prowadzić dokumentację
              uczestników i rozliczenia). Nie usuwamy tych danych po żadnym z góry ustalonym okresie — decydujesz o tym Ty,
              usuwając konto.
            </p>
            <p>
              <span className="text-surface-200 font-medium">Usunięcie konta</span> — konto możesz usunąć samodzielnie
              w ustawieniach. Usunięcie trwale kasuje Twoje dane profilowe (w tym zdjęcie profilowe) oraz zapisy na
              nadchodzące wydarzenia (zwalniając miejsce), a wpisy z wydarzeń już zakończonych
              <span className="text-surface-200 font-medium"> anonimizuje</span> — Twoje imię, nazwisko, e-mail i telefon
              są bezpowrotnie nadpisywane, a na liście uczestników zostaje wyłącznie anonimowy wpis bez powiązania z Tobą.
              Tak zanonimizowane dane nie są już danymi osobowymi w rozumieniu RODO. Jest to nasz mechanizm realizacji
              prawa do bycia zapomnianym.
            </p>
            <p>
              <span className="text-surface-200 font-medium">Tokeny bezpieczeństwa</span> (weryfikacja e-mail: 15 min,
              reset hasła: 1h, sesja: 7 dni) — usuwane automatycznie po wygaśnięciu przez wbudowany mechanizm czyszczenia.
            </p>
          </div>
        </Section>

        <Section title="5. Komu udostępniamy dane">
          <p className="text-surface-300 leading-relaxed font-medium text-lg mb-4">
            Nikomu. I nigdy tego nie zrobimy.
          </p>
          <p className="text-surface-400 leading-relaxed mb-4">
            Twoje dane osobowe nie są sprzedawane, wynajmowane ani przekazywane żadnym podmiotom trzecim w celach
            marketingowych, reklamowych ani żadnych innych celach komercyjnych.
          </p>
          <p className="text-surface-400 leading-relaxed mb-4">
            Jedynymi podmiotami, z którymi współpracujemy w ramach technicznego przetwarzania danych, są:
          </p>
          <div className="space-y-3">
            <InfoItem
              title="Dostawca usług hostingowych (serwer w UE)"
              description="Serwer aplikacji i baza danych zlokalizowane w Europejskim Obszarze Gospodarczym. Dane nie opuszczają EOG."
            />
            <InfoItem
              title="Zewnętrzny serwer poczty e-mail (SMTP)"
              description="Wykorzystywany wyłącznie do dostarczenia wiadomości (potwierdzenie zapisu, weryfikacja konta, reset hasła). Dostawca nie przetwarza Twoich danych w żadnym innym celu."
            />
          </div>
        </Section>

        <Section title="6. Twoje prawa">
          <p className="text-surface-400 leading-relaxed mb-4">
            Na podstawie RODO przysługują Ci następujące prawa:
          </p>
          <div className="space-y-3">
            <Right title="Prawo dostępu" description="Możesz w każdej chwili zapytać, jakie Twoje dane przechowujemy." />
            <Right title="Prawo do sprostowania" description="Jeśli Twoje dane są nieprawidłowe lub niekompletne, możesz żądać ich poprawienia." />
            <Right title="Prawo do usunięcia" description="Możesz zażądać trwałego usunięcia swoich danych — a konto usuniesz też samodzielnie w ustawieniach, co od razu anonimizuje całą Twoją historię zapisów." />
            <Right title="Prawo do ograniczenia przetwarzania" description="Możesz zażądać ograniczenia przetwarzania Twoich danych w określonych przypadkach." />
            <Right title="Prawo do przenoszalności" description="Możesz zażądać przekazania Twoich danych w ustrukturyzowanym, powszechnie używanym formacie." />
            <Right title="Prawo sprzeciwu" description="Możesz wnieść sprzeciw wobec przetwarzania danych opartego na uzasadnionym interesie." />
          </div>
          <p className="text-surface-400 text-sm mt-6 leading-relaxed">
            Aby skorzystać z któregokolwiek z powyższych praw, napisz do nas na adres{' '}
            <a href="mailto:fireacademy.biz@gmail.com" className="text-primary-400 hover:text-primary-300 transition-colors">
              fireacademy.biz@gmail.com
            </a>.
            Przysługuje Ci również prawo wniesienia skargi do organu nadzorczego — Prezesa Urzędu Ochrony Danych Osobowych
            (PUODO), ul. Stawki 2, 00-193 Warszawa.
          </p>
        </Section>

        <Section title="7. Bezpieczeństwo danych">
          <p className="text-surface-400 leading-relaxed mb-4">
            Stosujemy wielowarstwowe zabezpieczenia techniczne, aby chronić Twoje dane:
          </p>
          <DataList items={[
            'Hasła przechowywane wyłącznie jako hash bcrypt — nawet my nie znamy Twojego hasła',
            'Tokeny bezpieczeństwa hashowane algorytmem SHA-256 przed zapisem w bazie danych',
            'Szyfrowane połączenie HTTPS na całej stronie',
            'Tokeny JWT z krótkim czasem życia (15 minut) — minimalizacja ryzyka przy ewentualnym wycieku',
            'Konta blokowane automatycznie po wielokrotnych nieudanych próbach logowania (5 prób → blokada na 15 minut)',
            'Ograniczenie liczby żądań (rate limiting) — ochrona przed atakami brute-force',
          ]} />
        </Section>

        <Section title="8. Zmiany polityki prywatności">
          <p className="text-surface-400 leading-relaxed">
            W przypadku istotnych zmian w polityce prywatności poinformujemy Cię o tym z wyprzedzeniem —
            przez e-mail lub komunikat na stronie. Data ostatniej aktualizacji jest zawsze widoczna na górze tej strony.
            Zachęcamy do jej okresowego przeglądania.
          </p>
        </Section>

        <Section title="9. Kontakt w sprawach danych osobowych">
          <p className="text-surface-400 leading-relaxed">
            Jeśli masz pytania dotyczące przetwarzania Twoich danych osobowych, chcesz skorzystać z przysługujących
            Ci praw lub masz jakiekolwiek wątpliwości — napisz do nas. Potraktujemy każde zgłoszenie poważnie
            i odpowiemy tak szybko, jak to możliwe.
          </p>
          <div className="mt-4 flex flex-col gap-2">
            <a
              href="mailto:fireacademy.biz@gmail.com"
              className="text-primary-400 hover:text-primary-300 transition-colors font-medium"
            >
              fireacademy.biz@gmail.com
            </a>
            <a
              href="tel:+48534823667"
              className="text-primary-400 hover:text-primary-300 transition-colors font-medium"
            >
              +48 534 823 667
            </a>
          </div>
        </Section>

        <div className="text-center pt-4 pb-8">
          <Link
            to="/"
            className="text-sm text-surface-500 hover:text-primary-400 transition-colors"
          >
            Wróć na stronę główną
          </Link>
        </div>

      </div>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-surface-900 border border-surface-800 rounded-2xl p-6 sm:p-8">
      <h2 className="text-xl font-semibold text-surface-100 mb-5">{title}</h2>
      {children}
    </div>
  )
}

function SubSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mb-4">
      <h3 className="text-sm font-semibold text-surface-300 uppercase tracking-wider mb-2">{title}</h3>
      {children}
    </div>
  )
}

function DataList({ items }: { items: string[] }) {
  return (
    <ul className="space-y-1.5">
      {items.map((item, i) => (
        <li key={i} className="flex items-start gap-2 text-surface-400 text-sm leading-relaxed">
          <span className="mt-1.5 w-1.5 h-1.5 rounded-full bg-primary-500 shrink-0" />
          {item}
        </li>
      ))}
    </ul>
  )
}

function LegalBasis({ purpose, basis }: { purpose: string; basis: string }) {
  return (
    <div className="bg-surface-800/50 rounded-xl p-4">
      <p className="text-surface-200 font-medium text-sm mb-1">{purpose}</p>
      <p className="text-surface-500 text-sm leading-relaxed">{basis}</p>
    </div>
  )
}

function InfoItem({ title, description }: { title: string; description: string }) {
  return (
    <div className="bg-surface-800/50 rounded-xl p-4">
      <p className="text-surface-200 font-medium text-sm mb-1">{title}</p>
      <p className="text-surface-500 text-sm leading-relaxed">{description}</p>
    </div>
  )
}

function Right({ title, description }: { title: string; description: string }) {
  return (
    <div className="flex items-start gap-3">
      <span className="mt-1 w-1.5 h-1.5 rounded-full bg-primary-500 shrink-0" />
      <div>
        <span className="text-surface-200 font-medium text-sm">{title}</span>
        <span className="text-surface-500 text-sm"> — {description}</span>
      </div>
    </div>
  )
}
