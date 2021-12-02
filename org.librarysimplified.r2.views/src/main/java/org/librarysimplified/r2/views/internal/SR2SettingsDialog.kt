package org.librarysimplified.r2.views.internal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.r2.api.SR2ColorScheme
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerType
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.api.SR2Font
import org.librarysimplified.r2.api.SR2PublisherCSS.SR2_PUBLISHER_DEFAULT_CSS_DISABLED
import org.librarysimplified.r2.api.SR2PublisherCSS.SR2_PUBLISHER_DEFAULT_CSS_ENABLED
import org.librarysimplified.r2.api.SR2Theme
import org.librarysimplified.r2.views.R

internal class SR2SettingsDialog(
  context: Context,
  private val controller: SR2ControllerType,
  private val brightness: SR2BrightnessServiceType
) : AlertDialog(context) {

  private val subscriptions = CompositeDisposable()
  private lateinit var fontButtons: Map<Font, View>
  private lateinit var fontButtonTexts: Map<Font, TextView>
  private lateinit var fontDetailHeader: TextView
  private lateinit var fontDetailBody: TextView
  private lateinit var colorButtons: Map<Color, View>
  private lateinit var colorButtonTexts: Map<Color, TextView>
  private lateinit var textSmallerButton: View
  private lateinit var textLargerButton: View
  private lateinit var brightnessBar: SeekBar

  @SuppressLint("InflateParams")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val view = LayoutInflater.from(context).inflate(R.layout.sr2_settings, null)
    setContentView(view)

    // Font
    fontButtons = mapOf(
      Font.SANS to view.findViewById(R.id.setFontSans)!!,
      Font.SERIF to view.findViewById(R.id.setFontSerif)!!,
      Font.DYSLEXIC to view.findViewById(R.id.setFontDyslexic)!!,
      Font.PUBLISHER to view.findViewById(R.id.setFontPub)!!
    )

    fontButtonTexts = mapOf(
      Font.SANS to view.findViewById(R.id.setFontSansText)!!,
      Font.SERIF to view.findViewById(R.id.setFontSerifText)!!,
      Font.DYSLEXIC to view.findViewById(R.id.setFontDyslexicText)!!,
      Font.PUBLISHER to view.findViewById(R.id.setFontPubText)!!
    )

    fontButtonTexts[Font.DYSLEXIC]!!.typeface =
      ResourcesCompat.getFont(context, R.font.open_dyslexic)

    fontButtons.forEach { entry ->
      entry.value.setOnClickListener { setFont(entry.key) }
    }

    // Font details
    fontDetailHeader = view.findViewById(R.id.setFontDetailHeader)
    fontDetailBody = view.findViewById(R.id.setFontDetailBody)

    // Color
    colorButtons = mapOf(
      Color.LIGHT to view.findViewById<View>(R.id.setThemeLight)!!,
      Color.SEPIA to view.findViewById<TextView>(R.id.setThemeSepia)!!,
      Color.DARK to view.findViewById<TextView>(R.id.setThemeDark)!!
    )

    colorButtonTexts = mapOf(
      Color.LIGHT to view.findViewById(R.id.setThemeLightText)!!,
      Color.SEPIA to view.findViewById(R.id.setThemeSepiaText)!!,
      Color.DARK to view.findViewById(R.id.setThemeDarkText)!!
    )

    colorButtons.forEach { entry ->
      entry.value.setOnClickListener { setColor(entry.key) }
    }

    // Size
    textSmallerButton = view.findViewById(R.id.setTextSmaller)!!
    textLargerButton = view.findViewById(R.id.setTextLarger)!!

    textLargerButton.setOnClickListener { increaseSize() }
    textSmallerButton.setOnClickListener { decreaseSize() }

    // Brightness
    brightnessBar = view.findViewById(R.id.setBrightness)!!
    brightnessBar.setOnSeekBarChangeListener(
      BrightnessListener(brightness)
    )

    // Initial values
    onThemeChanged(controller.themeNow())

    // Observe theme changes
    subscriptions.add(
      controller.events
        .ofType(SR2Event.SR2ThemeChanged::class.java)
        .subscribe { event -> onThemeChanged((event.theme)) }
    )

    setOnDismissListener {
      subscriptions.clear()
    }
  }

  private fun onThemeChanged(theme: SR2Theme) {
    val font = Font.fromTheme(theme)
    fontButtonTexts.forEach { entry ->
      entry.value.setUnderlined(entry.key == font)
      entry.value.showBottomBorder(entry.key != font)
    }
    fontDetailHeader.text = font.title(context)
    fontDetailBody.text = font.description(context)

    val color = Color.fromTheme(theme)
    colorButtonTexts.forEach { entry ->
      entry.value.setUnderlined(entry.key == color)
    }
  }

  private fun TextView.setUnderlined(value: Boolean) {
    paintFlags =
      if (value) {
        paintFlags or Paint.UNDERLINE_TEXT_FLAG
      } else {
        paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
      }
  }

  private fun View.showBottomBorder(value: Boolean) {
    val drawableId =
      if (value) R.drawable.sr2_transparent_bordered
      else R.drawable.sr2_transparent_bordered_without_bottom
    background = ContextCompat.getDrawable(context, drawableId)
  }

  private fun increaseSize() {
    this.updateTheme { it.copy(textSize = SR2Theme.sizeConstrain(it.textSize + 0.1)) }
  }

  private fun decreaseSize() {
    this.updateTheme { it.copy(textSize = SR2Theme.sizeConstrain(it.textSize - 0.1)) }
  }

  private fun setFont(font: Font) {
    updateTheme(font.themeUpdater)
  }

  private fun setColor(color: Color) {
    updateTheme(color.themeUpdater)
  }

  private fun updateTheme(updater: (SR2Theme) -> SR2Theme) {
    controller.submitCommand(SR2Command.ThemeSet(updater.invoke(controller.themeNow())))
  }

  private enum class Font {
    SANS,
    SERIF,
    DYSLEXIC,
    PUBLISHER;

    val themeUpdater: (SR2Theme) -> SR2Theme = {
      val font = when (this) {
        SANS -> SR2Font.FONT_SANS
        SERIF -> SR2Font.FONT_SERIF
        DYSLEXIC -> SR2Font.FONT_OPEN_DYSLEXIC
        PUBLISHER -> it.font
      }
      val pubDefault = when (this) {
        PUBLISHER -> SR2_PUBLISHER_DEFAULT_CSS_ENABLED
        else -> SR2_PUBLISHER_DEFAULT_CSS_DISABLED
      }
      it.copy(font = font, publisherCSS = pubDefault)
    }

    fun title(context: Context): String =
      when (this) {
        SANS -> context.getString(R.string.settingsSansHeader)
        SERIF -> context.getString(R.string.settingsSerifHeader)
        DYSLEXIC -> context.getString(R.string.settingsDyslexicHeader)
        PUBLISHER -> context.getString(R.string.settingsPublisherHeader)
      }

    fun description(context: Context): String =
      when (this) {
        SANS -> context.getString(R.string.settingsSansBody)
        SERIF -> context.getString(R.string.settingsSerifBody)
        DYSLEXIC -> context.getString(R.string.settingsDyslexicBody)
        PUBLISHER -> context.getString(R.string.settingsPublisherBody)
      }

    companion object {

      fun fromTheme(theme: SR2Theme) =
        if (theme.publisherCSS == SR2_PUBLISHER_DEFAULT_CSS_ENABLED) {
          PUBLISHER
        } else when (theme.font) {
          SR2Font.FONT_SANS -> SANS
          SR2Font.FONT_SERIF -> SERIF
          SR2Font.FONT_OPEN_DYSLEXIC -> DYSLEXIC
        }
    }
  }

  private enum class Color {
    LIGHT,
    DARK,
    SEPIA;

    val themeUpdater: (SR2Theme) -> SR2Theme = {
      val color = when (this) {
        LIGHT -> SR2ColorScheme.DARK_TEXT_LIGHT_BACKGROUND
        DARK -> SR2ColorScheme.LIGHT_TEXT_DARK_BACKGROUND
        SEPIA -> SR2ColorScheme.DARK_TEXT_ON_SEPIA
      }
      it.copy(colorScheme = color)
    }

    companion object {

      fun fromTheme(theme: SR2Theme): Color = when (theme.colorScheme) {
        SR2ColorScheme.DARK_TEXT_LIGHT_BACKGROUND -> LIGHT
        SR2ColorScheme.LIGHT_TEXT_DARK_BACKGROUND -> DARK
        SR2ColorScheme.DARK_TEXT_ON_SEPIA -> SEPIA
      }
    }
  }

  class BrightnessListener(
    private val brightness: SR2BrightnessServiceType
  ) : SeekBar.OnSeekBarChangeListener {

    private var bright = brightness.brightness()

    override fun onProgressChanged(
      seekBar: SeekBar,
      progress: Int,
      fromUser: Boolean
    ) {
      this.bright = progress / 100.0
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
      brightness.setBrightness(this.bright)
    }
  }
}
