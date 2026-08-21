package com.offline.mathcrossword;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Converts one completed local telemetry summary into a short player-facing story.
 * It intentionally avoids raw research jargon and never presents weak inference as fact.
 */
final class PostSolveInsightBuilder {
    private PostSolveInsightBuilder() { }

    static final class Result {
        final List<String> observations = new ArrayList<>();
        boolean hasStrongSignal;
    }

    static Result build(SessionTracker.SessionSummary s) {
        Result out = new Result();
        if (s == null || !s.solved) return out;

        // 1) Prefer a clear structural route signal when graph mapping is confident.
        if (s.traversalAvailable && s.traversalConfidencePct >= 65.0) {
            String route = routeObservation(s);
            if (route != null) {
                out.observations.add(route);
                out.hasStrongSignal = true;
            }
        }

        // 2) Model-vs-play divergence is valuable: it may reveal a natural route the model missed.
        if (out.observations.size() < 4 && s.routeCompared) {
            if (s.routeStrongDivergence) {
                out.observations.add(UiText.tr(
                        "Your route differed clearly from the model route — this puzzle may have another natural way in.",
                        "Твой маршрут заметно отличался от модельного — похоже, у этой задачи есть другой естественный путь входа.",
                        "Tvoje cesta se zřetelně lišila od modelové — hlavolam může mít jiný přirozený vstup."));
                out.hasStrongSignal = true;
            } else if (s.routeAlternateEntry && s.routeEarlyAgreementPct < 55.0) {
                out.observations.add(UiText.tr(
                        "You started from a different place than the model, then still converged on a valid route.",
                        "Ты начал не с той точки, которую выбрала модель, но затем всё равно свёл решение к рабочему маршруту.",
                        "Začal jsi jinde než model, ale pak ses přesto dostal na funkční cestu řešení."));
                out.hasStrongSignal = true;
            } else if (s.routeAgreementPct >= 75.0) {
                out.observations.add(UiText.tr(
                        "Your move order was close to the model route — the puzzle unfolded in a fairly direct way.",
                        "Порядок твоих ходов был близок к модельному маршруту — задача раскрывалась довольно прямым путём.",
                        "Pořadí tvých tahů bylo blízko modelové cestě — hlavolam se rozvíjel poměrně přímo."));
            }
        }

        // 3) Separate puzzle structure from observed play. A model cascade is not claimed as a player action.
        if (out.observations.size() < 4 && s.hidden > 0 && s.maxForcedCascade >= 4) {
            double fraction = s.maxForcedCascade / (double) s.hidden;
            if (fraction >= 0.45) {
                if (s.rapidCascades > 0) {
                    out.observations.add(UiText.format(
                            "After a key deduction, the puzzle structure could unlock up to %d of %d cells; your play also contained a rapid cascade.",
                            "После ключевого вывода структура задачи могла открыть до %d из %d клеток; и в твоём прохождении действительно появился быстрый каскад.",
                            "Po klíčovém závěru mohla struktura odemknout až %d z %d buněk; i v tvém průchodu se objevil rychlý kaskádový úsek.",
                            s.maxForcedCascade, s.hidden));
                    out.hasStrongSignal = true;
                } else {
                    out.observations.add(UiText.format(
                            "This puzzle had a strong latent cascade: one key deduction could unlock up to %d of %d cells.",
                            "В задаче был сильный скрытый каскад: один ключевой вывод мог открыть до %d из %d клеток.",
                            "Hlavolam měl silný skrytý kaskádový efekt: jeden klíčový závěr mohl odemknout až %d z %d buněk.",
                            s.maxForcedCascade, s.hidden));
                }
            }
        }

        // 4) Hypothesis episodes are heuristic signals, so phrase them as such.
        if (out.observations.size() < 4 && s.hypothesisEpisodes > 0) {
            out.observations.add(UiText.format(
                    "The trace contains %d episode(s) that look like checking an alternative before committing.",
                    "В ходе решения видно %d эпизод(а), похожих на проверку альтернативы перед окончательным выбором.",
                    "V průběhu je vidět %d epizod připomínajících ověření alternativy před konečným rozhodnutím.",
                    s.hypothesisEpisodes));
            out.hasStrongSignal = true;
        }

        // 5) Candidate revisits indicate non-linear refinement without judging it as good or bad.
        if (out.observations.size() < 4 && s.candidateCellRevisits >= 2) {
            out.observations.add(UiText.format(
                    "You returned to already considered cells %d times — the solution developed through revision rather than one straight pass.",
                    "Ты %d раз возвращался к уже рассмотренным клеткам — решение развивалось через пересмотр вариантов, а не одним линейным проходом.",
                    "%dkrát ses vrátil k už zvažovaným buňkám — řešení se vyvíjelo přes revizi možností, ne jedním lineárním průchodem.",
                    s.candidateCellRevisits));
        }

        // 6) Time is descriptive, never a score. Only surface it when there was a visible observation phase.
        if (out.observations.size() < 4 && s.firstActionMs >= 12000L) {
            out.observations.add(UiText.tr(
                    "You spent some time reading the board before the first recorded action — the solve began with observation, not immediate tapping.",
                    "Перед первым зафиксированным действием ты некоторое время читал поле — решение началось с наблюдения, а не с мгновенных нажатий.",
                    "Před první zaznamenanou akcí jsi chvíli četl pole — řešení začalo pozorováním, ne okamžitým klikáním."));
        }

        if (out.observations.isEmpty()) {
            out.observations.add(UiText.tr(
                    "No single pattern dominated this solve. The trace looks relatively even rather than built around one dramatic turning point.",
                    "В этом решении не выделился один доминирующий паттерн. Прохождение выглядит скорее ровным, без одного резкого поворотного момента.",
                    "V tomto řešení nepřevládl jeden výrazný vzorec. Průchod působí spíš rovnoměrně, bez jediného dramatického zlomu."));
        }
        return out;
    }

    private static String routeObservation(SessionTracker.SessionSummary s) {
        String direction = s.traversalDirection == null ? "" : s.traversalDirection.toLowerCase(Locale.US);
        switch (direction) {
            case "forward":
                return s.traversalInternalEntry
                        ? UiText.tr(
                                "You entered the constraint structure from the inside and then moved mostly forward from that point.",
                                "Ты вошёл в структуру ограничений изнутри и дальше двигался в основном вперёд от найденной точки.",
                                "Do struktury omezení jsi vstoupil zevnitř a pak ses odtud pohyboval převážně dopředu.")
                        : UiText.tr(
                                "Your solve moved mostly forward through connected constraints from the entry point.",
                                "Решение шло в основном вперёд по связанным ограничениям от найденной точки входа.",
                                "Řešení postupovalo převážně dopředu přes propojená omezení od vstupního bodu.");
            case "backward":
                return UiText.tr(
                        "You often worked backward through the structure rather than following it from the model anchor.",
                        "Ты часто двигался по структуре назад, а не следовал ей от модельной опорной точки.",
                        "Často ses strukturou vracel zpět místo postupu od modelového opěrného bodu.");
            case "bidirectional":
                return UiText.tr(
                        "You worked from both directions and let the two lines of reasoning meet in the middle.",
                        "Ты решал с двух направлений и позволил двум линиям рассуждения встретиться в середине.",
                        "Řešil jsi z obou směrů a nechal dvě linie uvažování setkat se uprostřed.");
            case "mixed":
                return UiText.tr(
                        "Your route switched direction several times — the solve was exploratory rather than purely linear.",
                        "Маршрут несколько раз менял направление — решение было исследовательским, а не чисто линейным.",
                        "Tvoje cesta několikrát změnila směr — řešení bylo průzkumné, ne čistě lineární.");
            case "divergent":
                return UiText.tr(
                        "Part of your route ran outside the model's main structure — worth treating as a possible alternative solving path.",
                        "Часть твоего маршрута прошла вне основной структуры модели — это похоже на возможный альтернативный путь решения.",
                        "Část tvé cesty vedla mimo hlavní strukturu modelu — může jít o alternativní cestu řešení.");
            default:
                return null;
        }
    }
}
