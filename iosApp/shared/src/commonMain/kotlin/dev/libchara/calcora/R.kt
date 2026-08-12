package dev.libchara.calcora

import dev.libchara.calcora.generated.resources.Res
import dev.libchara.calcora.generated.resources.*

/** Android-style resource facade so the existing Compose screens compile unchanged. */
object R {
    object font { const val ibm_3270_regular: Int = 1 }
    object string {
        val about_text = Res.string.about_text
        val angle_deg = Res.string.angle_deg
        val angle_rad = Res.string.angle_rad
        val app_name = Res.string.app_name
        val btn_back = Res.string.btn_back
        val btn_clear = Res.string.btn_clear
        val btn_copy = Res.string.btn_copy
        val btn_copy_short = Res.string.btn_copy_short
        val btn_evaluate = Res.string.btn_evaluate
        val btn_grid = Res.string.btn_grid
        val btn_insert = Res.string.btn_insert
        val btn_no_grid = Res.string.btn_no_grid
        val btn_reset = Res.string.btn_reset
        val btn_run = Res.string.btn_run
        val btn_view_plot = Res.string.btn_view_plot
        val eval_approx = Res.string.eval_approx
        val eval_auto = Res.string.eval_auto
        val eval_exact = Res.string.eval_exact
        val eval_raw = Res.string.eval_raw
        val help_all_commands = Res.string.help_all_commands
        const val help_category_algebra = 1
        const val help_category_algebra_desc = 2
        const val help_category_calculus = 3
        const val help_category_calculus_desc = 4
        const val help_category_linear_algebra = 5
        const val help_category_linear_algebra_desc = 6
        const val help_category_lists = 7
        const val help_category_lists_desc = 8
        const val help_category_plotting = 9
        const val help_category_plotting_desc = 10
        const val help_category_statistics = 11
        const val help_category_statistics_desc = 12
        val help_command_count = Res.string.help_command_count
        val help_desc = Res.string.help_desc
        val help_empty = Res.string.help_empty
        val help_example_hint = Res.string.help_example_hint
        val help_examples = Res.string.help_examples
        val help_explore = Res.string.help_explore
        val help_insert_function = Res.string.help_insert_function
        val help_loading = Res.string.help_loading
        val help_matches = Res.string.help_matches
        val help_no_matches_desc = Res.string.help_no_matches_desc
        val help_no_matches_title = Res.string.help_no_matches_title
        val help_no_result = Res.string.help_no_result
        val help_related = Res.string.help_related
        val help_result_count = Res.string.help_result_count
        val help_search_hint = Res.string.help_search_hint
        val help_see_also = Res.string.help_see_also
        val help_syntax = Res.string.help_syntax
        val help_title = Res.string.help_title
        val help_use_example = Res.string.help_use_example
        val hist_clear_all = Res.string.hist_clear_all
        val hist_empty = Res.string.hist_empty
        val hist_plot = Res.string.hist_plot
        val hist_title = Res.string.hist_title
        val lang_chinese = Res.string.lang_chinese
        val lang_english = Res.string.lang_english
        val lang_system = Res.string.lang_system
        val panel_funcs = Res.string.panel_funcs
        val panel_fx = Res.string.panel_fx
        val panel_vars = Res.string.panel_vars
        val plot_hint = Res.string.plot_hint
        val plot_hint_3d = Res.string.plot_hint_3d
        val plot_preview = Res.string.plot_preview
        val script_cancel = Res.string.script_cancel
        val script_delete = Res.string.script_delete
        val script_editor_hint = Res.string.script_editor_hint
        val script_load = Res.string.script_load
        val script_name_hint = Res.string.script_name_hint
        val script_no_files = Res.string.script_no_files
        val script_output = Res.string.script_output
        val script_output_hint = Res.string.script_output_hint
        val script_save = Res.string.script_save
        val script_title = Res.string.script_title
        val settings_about = Res.string.settings_about
        val settings_actions = Res.string.settings_actions
        val settings_angle = Res.string.settings_angle
        val settings_autocomplete = Res.string.settings_autocomplete
        val settings_autocomplete_desc = Res.string.settings_autocomplete_desc
        val settings_check_update = Res.string.settings_check_update
        val settings_clear_history = Res.string.settings_clear_history
        val settings_digits = Res.string.settings_digits
        val settings_history_limit = Res.string.settings_history_limit
        val settings_lang = Res.string.settings_lang
        val settings_mode = Res.string.settings_mode
        val settings_open_release = Res.string.settings_open_release
        val settings_precision = Res.string.settings_precision
        val settings_reset_session = Res.string.settings_reset_session
        val settings_syntax_hl = Res.string.settings_syntax_hl
        val settings_syntax_hl_desc = Res.string.settings_syntax_hl_desc
        val settings_theme = Res.string.settings_theme
        val settings_title = Res.string.settings_title
        val settings_update_available = Res.string.settings_update_available
        val settings_update_checking = Res.string.settings_update_checking
        val settings_update_current = Res.string.settings_update_current
        val settings_update_failed = Res.string.settings_update_failed
        val settings_update_latest = Res.string.settings_update_latest
        val tab_calc = Res.string.tab_calc
        val tab_help = Res.string.tab_help
        val tab_hist = Res.string.tab_hist
        val tab_set = Res.string.tab_set
        val tab_term = Res.string.tab_term
        val term_hint = Res.string.term_hint
        val term_input_hint = Res.string.term_input_hint
        val term_output_title = Res.string.term_output_title
        val term_title = Res.string.term_title
        val theme_dark = Res.string.theme_dark
        val theme_light = Res.string.theme_light
        val theme_system = Res.string.theme_system
        val update_dialog_later = Res.string.update_dialog_later
        val update_dialog_message = Res.string.update_dialog_message
        val update_dialog_title = Res.string.update_dialog_title
    }

    internal fun stringResource(id: Int) = when (id) {
        string.help_category_algebra -> Res.string.help_category_algebra
        string.help_category_algebra_desc -> Res.string.help_category_algebra_desc
        string.help_category_calculus -> Res.string.help_category_calculus
        string.help_category_calculus_desc -> Res.string.help_category_calculus_desc
        string.help_category_linear_algebra -> Res.string.help_category_linear_algebra
        string.help_category_linear_algebra_desc -> Res.string.help_category_linear_algebra_desc
        string.help_category_lists -> Res.string.help_category_lists
        string.help_category_lists_desc -> Res.string.help_category_lists_desc
        string.help_category_plotting -> Res.string.help_category_plotting
        string.help_category_plotting_desc -> Res.string.help_category_plotting_desc
        string.help_category_statistics -> Res.string.help_category_statistics
        string.help_category_statistics_desc -> Res.string.help_category_statistics_desc
        else -> error("Unknown string resource id: $id")
    }
}
