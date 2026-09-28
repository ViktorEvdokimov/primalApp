package com.primal.progression;

import com.primal.progression.BattleResultDto.RewardRule;
import com.primal.rules.effects.Effect;
import com.primal.rules.effects.EffectPlanner;
import com.primal.rules.effects.RuleExplanation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Пояснения условных правил для окон наград: вид правила берётся из условия, текст — из плана. */
final class RuleViews {

    private RuleViews() {
    }

    /**
     * Пояснения плана идут по порядку условий верхнего уровня набора эффектов ({@code EffectPlanner.plan}),
     * поэтому вид каждого пояснения — вид соответствующего условия.
     */
    static List<RewardRule> of(List<Effect> effects, List<RuleExplanation> explanations) {
        List<RewardRule> views = new ArrayList<>();
        Iterator<RuleExplanation> explanation = explanations.iterator();
        for (Effect effect : effects) {
            if (effect instanceof Effect.Conditional conditional && explanation.hasNext()) {
                RuleExplanation rule = explanation.next();
                views.add(new RewardRule(EffectPlanner.kind(conditional).name(), rule.description(), rule.result()));
            }
        }
        return views;
    }
}
