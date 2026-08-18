package com.offline.mathcrossword;

/** Human-readable privacy disclosure shown inside the app. */
final class PrivacyPolicy {
    private PrivacyPolicy() { }

    static String text() {
        return UiText.tr(
                "MathCrossword Privacy Policy\n"
                        + "Updated: August 16, 2026\n\n"
                        + "MathCrossword is an offline mathematical puzzle app. The game does not require an account, sign-in, advertising, or a cloud server.\n\n"
                        + "What is stored on the phone\n"
                        + "Progress and local game-session history. The history may contain the puzzle seed, generator/solver versions, active-play time, cell and candidate actions, Undo, hints, navigation events, puzzle-graph characteristics, HumanSolver route comparison with the observed play trace, and derived difficulty statistics.\n\n"
                        + "Automatic transmission\n"
                        + "The Google Play version does not automatically send this history to the developer or analytics services. It contains no advertising, account system, or background research synchronization.\n\n"
                        + "Research data export\n"
                        + "An archive is created only after an explicit user action. It may contain metadata.json, sessions.jsonl, and summary.json. Metadata uses a random installation identifier so several exports from one installation can be associated without using a Google account, advertising ID, phone number, contacts, or location. After creation, Android lets the user choose where to save the file or whom to share it with. The app does not select a recipient and does not send the archive silently.\n\n"
                        + "Personal data\n"
                        + "The app intentionally does not request a name, email address, phone number, contacts, location, photos, microphone access, payment information, or medical data.\n\n"
                        + "Storage and deletion\n"
                        + "Local data remains in app storage until the app data is cleared or the app is uninstalled. An exported ZIP is a separate file; after export, storage and sharing are controlled by the user and the chosen recipient.\n\n"
                        + "Changes\n"
                        + "If Research Sync, accounts, analytics, advertising, or any other automatic transmission outside the device is added later, this policy and the Google Play Data safety declaration must be updated before that version is released.\n\n"
                        + "Privacy contact\n"
                        + "Use the Issues section of the public MathCrossword GitHub repository. Do not publish sensitive personal information in a public issue.",
                "Политика конфиденциальности MathCrossword\n"
                        + "Обновлено: 16 августа 2026\n\n"
                        + "MathCrossword — офлайн-приложение с математическими головоломками. Игра не требует аккаунта, входа, рекламы или облачного сервиса.\n\n"
                        + "Что хранится на телефоне\n"
                        + "Прогресс и локальная история игровых сессий. История может содержать seed задачи, версии генератора/решателя, время активной игры, действия с клетками и кандидатами, Undo, подсказки, навигационные события, характеристики графа задачи, сравнение маршрута HumanSolver с фактическим прохождением и производные статистики сложности.\n\n"
                        + "Автоматическая отправка\n"
                        + "Google Play-версия не отправляет эту историю разработчику или аналитическим сервисам автоматически. В ней нет рекламы, аккаунта и фоновой исследовательской синхронизации.\n\n"
                        + "Экспорт исследовательских данных\n"
                        + "Архив создаётся только после явного нажатия пользователем. Он может содержать metadata.json, sessions.jsonl и summary.json. В metadata используется случайный идентификатор установки, чтобы можно было связать несколько экспортов одной установки без Google-аккаунта, рекламного ID, телефона, контактов или геолокации. После создания Android позволяет самому выбрать место сохранения или получателя. Приложение не выбирает получателя и не отправляет архив скрытно.\n\n"
                        + "Персональные данные\n"
                        + "Приложение намеренно не запрашивает имя, email, номер телефона, контакты, местоположение, фотографии, микрофон, платёжные или медицинские данные.\n\n"
                        + "Хранение и удаление\n"
                        + "Локальные данные остаются в хранилище приложения до очистки данных приложения или его удаления. Экспортированный ZIP — отдельный файл; после экспорта его хранением управляет пользователь и выбранный получатель.\n\n"
                        + "Изменения\n"
                        + "Если позже появятся Research Sync, аккаунты, аналитика, реклама или другая автоматическая передача данных за пределы устройства, эта политика и декларация Google Play Data safety должны быть обновлены до выпуска такой версии.\n\n"
                        + "Контакт по вопросам конфиденциальности\n"
                        + "Используй раздел Issues публичного репозитория MathCrossword на GitHub. Не публикуй в открытом issue чувствительные личные данные.",
                "Zásady ochrany soukromí MathCrossword\n"
                        + "Aktualizováno: 16. srpna 2026\n\n"
                        + "MathCrossword je offline aplikace s matematickými hlavolamy. Hra nevyžaduje účet, přihlášení, reklamu ani cloudový server.\n\n"
                        + "Co se ukládá v telefonu\n"
                        + "Postup a místní historie herních relací. Historie může obsahovat seed úlohy, verze generátoru/řešiče, čas aktivní hry, akce s políčky a kandidáty, Undo, nápovědy, navigační události, charakteristiky grafu úlohy, porovnání trasy HumanSolveru s pozorovaným průchodem a odvozené statistiky obtížnosti.\n\n"
                        + "Automatické odesílání\n"
                        + "Verze pro Google Play tuto historii automaticky neodesílá vývojáři ani analytickým službám. Neobsahuje reklamu, systém účtů ani výzkumnou synchronizaci na pozadí.\n\n"
                        + "Export výzkumných dat\n"
                        + "Archiv se vytvoří pouze po výslovné akci uživatele. Může obsahovat metadata.json, sessions.jsonl a summary.json. Metadata používají náhodný identifikátor instalace, aby bylo možné propojit více exportů z jedné instalace bez účtu Google, reklamního ID, telefonního čísla, kontaktů nebo polohy. Po vytvoření Android umožní uživateli zvolit, kam soubor uložit nebo komu jej sdílet. Aplikace příjemce nevybírá a archiv neodesílá skrytě.\n\n"
                        + "Osobní údaje\n"
                        + "Aplikace záměrně nepožaduje jméno, e-mail, telefonní číslo, kontakty, polohu, fotografie, mikrofon, platební ani zdravotní údaje.\n\n"
                        + "Ukládání a odstranění\n"
                        + "Místní data zůstávají v úložišti aplikace, dokud uživatel nevymaže data aplikace nebo aplikaci neodinstaluje. Exportovaný ZIP je samostatný soubor; po exportu jeho uložení a sdílení řídí uživatel a zvolený příjemce.\n\n"
                        + "Změny\n"
                        + "Pokud později přibude Research Sync, účty, analytika, reklama nebo jiný automatický přenos dat mimo zařízení, musí být tyto zásady a deklarace Google Play Data safety aktualizovány ještě před vydáním takové verze.\n\n"
                        + "Kontakt k ochraně soukromí\n"
                        + "Použij sekci Issues ve veřejném repozitáři MathCrossword na GitHubu. Do veřejného issue nevkládej citlivé osobní údaje.");
    }
}
