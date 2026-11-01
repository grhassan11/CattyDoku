package com.threetech.catpuzzle.ui.screens.game

import androidx.annotation.StringRes
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.model.AnimalSet
import com.threetech.catpuzzle.domain.model.PieceSkin

/** What the game-board text calls a piece: cats (normal levels), dogs (Dogs boss set) or animals (other boss sets). */
internal enum class PieceWord { CAT, DOG, ANIMAL }

internal fun PieceSkin?.word(): PieceWord = when (this?.set) {
    null -> PieceWord.CAT
    AnimalSet.DOGS -> PieceWord.DOG
    AnimalSet.ANIMALS, AnimalSet.OCEAN -> PieceWord.ANIMAL
}

/** String-resource picks per [PieceWord]; keeps all wording switches in one place. */
internal object PieceWording {
    @StringRes fun rulePerColor(w: PieceWord) = pick(w, R.string.rule_one_per_color, R.string.rule_one_per_color_dog, R.string.rule_one_per_color_animal)
    @StringRes fun rulePerLine(w: PieceWord) = pick(w, R.string.rule_one_per_line, R.string.rule_one_per_line_dog, R.string.rule_one_per_line_animal)
    @StringRes fun ruleNoTouch(w: PieceWord) = pick(w, R.string.rule_no_touch, R.string.rule_no_touch_dog, R.string.rule_no_touch_animal)

    @StringRes fun boosterPlace(w: PieceWord) = pick(w, R.string.booster_cat, R.string.booster_dog, R.string.booster_animal)
    @StringRes fun boosterNone(w: PieceWord) = pick(w, R.string.booster_no_cat, R.string.booster_no_dog, R.string.booster_no_animal)
    @StringRes fun boosterWatch(w: PieceWord) = pick(w, R.string.booster_cat_watch, R.string.booster_dog_watch, R.string.booster_animal_watch)

    @StringRes fun rewardTitle(w: PieceWord) = pick(w, R.string.reward_cat_title, R.string.reward_dog_title, R.string.reward_animal_title)
    @StringRes fun rewardSubtitle(w: PieceWord) = pick(w, R.string.reward_cat_subtitle, R.string.reward_dog_subtitle, R.string.reward_animal_subtitle)
    @StringRes fun rewardStore(w: PieceWord) = pick(w, R.string.reward_cat_store, R.string.reward_dog_store, R.string.reward_animal_store)

    @StringRes fun message(m: HintMessage, w: PieceWord): Int = when (m) {
        HintMessage.WRONG_CAT -> pick(w, R.string.hint_wrong_cat, R.string.hint_wrong_dog, R.string.hint_wrong_animal)
        HintMessage.X_ON_SOLUTION -> pick(w, R.string.hint_x_on_solution, R.string.hint_x_on_solution_dog, R.string.hint_x_on_solution_animal)
        HintMessage.PLACE_SINGLE -> pick(w, R.string.hint_place_single, R.string.hint_place_single_dog, R.string.hint_place_single_animal)
        HintMessage.PLACE_REVEAL -> pick(w, R.string.hint_place_reveal, R.string.hint_place_reveal_dog, R.string.hint_place_reveal_animal)
        HintMessage.ELIMINATE_CONFINEMENT -> pick(w, R.string.hint_eliminate_confinement, R.string.hint_eliminate_confinement_dog, R.string.hint_eliminate_confinement_animal)
        HintMessage.ELIMINATE_CROWDING -> pick(w, R.string.hint_eliminate_crowding, R.string.hint_eliminate_crowding_dog, R.string.hint_eliminate_crowding_animal)
        HintMessage.ELIMINATE_LOOKAHEAD -> pick(w, R.string.hint_eliminate_lookahead, R.string.hint_eliminate_lookahead_dog, R.string.hint_eliminate_lookahead_animal)
    }

    @StringRes fun rule(r: com.threetech.catpuzzle.domain.model.HintRule, w: PieceWord): Int = when (r) {
        com.threetech.catpuzzle.domain.model.HintRule.ROW -> pick(w, R.string.hint_rule_row, R.string.hint_rule_row_dog, R.string.hint_rule_row_animal)
        com.threetech.catpuzzle.domain.model.HintRule.COLUMN -> pick(w, R.string.hint_rule_column, R.string.hint_rule_column_dog, R.string.hint_rule_column_animal)
        com.threetech.catpuzzle.domain.model.HintRule.REGION -> pick(w, R.string.hint_rule_region, R.string.hint_rule_region_dog, R.string.hint_rule_region_animal)
        com.threetech.catpuzzle.domain.model.HintRule.TOUCH -> pick(w, R.string.hint_rule_touch, R.string.hint_rule_touch_dog, R.string.hint_rule_touch_animal)
    }

    @StringRes fun cellState(w: PieceWord) = pick(w, R.string.cell_state_cat, R.string.cell_state_dog, R.string.cell_state_animal)
    @StringRes fun cellPlace(w: PieceWord) = pick(w, R.string.cell_action_place_cat, R.string.cell_action_place_dog, R.string.cell_action_place_animal)
    @StringRes fun cellRemove(w: PieceWord) = pick(w, R.string.cell_action_remove_cat, R.string.cell_action_remove_dog, R.string.cell_action_remove_animal)

    @StringRes
    private fun pick(w: PieceWord, @StringRes cat: Int, @StringRes dog: Int, @StringRes animal: Int): Int =
        when (w) {
            PieceWord.CAT -> cat
            PieceWord.DOG -> dog
            PieceWord.ANIMAL -> animal
        }
}
