package com.pawquest.branding

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.pawquest.R
import com.pawquest.domain.model.WidgetPetMood
import com.pawquest.widget.PetWidgetArtwork

/** Debug-only, synthetic artwork gallery. Never reads/writes account or widget preferences. */
class BrandingPreviewActivity : Activity() {
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun label(value: String)=TextView(this).apply {
        text=value;textSize=14f;setTextColor(Color.rgb(66,44,88));setPadding(0,dp(8),0,dp(8))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.rgb(243,237,249)
        window.navigationBarColor=Color.rgb(243,237,249)
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val content=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(16))}
        val scroll=ScrollView(this).apply {setBackgroundColor(Color.rgb(243,237,249));addView(content)}
        setContentView(scroll)
        content.addView(label("PawQuest · New launcher identity"))
        val row=LinearLayout(this)
        listOf(R.mipmap.pawquest_launcher_normal to "Normal",R.mipmap.pawquest_launcher_hungry to "Hungry",R.mipmap.pawquest_launcher_bored to "Bored").forEach {(id,name)->
            val drawable=checkNotNull(getDrawable(id))
            if(android.os.Build.VERSION.SDK_INT>=26) check(drawable is AdaptiveIconDrawable)
            if(android.os.Build.VERSION.SDK_INT>=33) check((drawable as AdaptiveIconDrawable).monochrome!=null)
            val cell=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER}
            cell.addView(ImageView(this).apply {setImageDrawable(drawable);contentDescription=name},LinearLayout.LayoutParams(dp(80),dp(80)))
            cell.addView(label(name));row.addView(cell,LinearLayout.LayoutParams(0,-2,1f))
        }
        content.addView(row)
        var checked=0
        for(species in listOf("prototype_cat","puppy","rabbit","capybara")) for(mood in WidgetPetMood.entries) {
            val drawable=checkNotNull(getDrawable(PetWidgetArtwork.portrait(species,mood))) as BitmapDrawable
            check(drawable.bitmap.byteCount<=1024*1024)
            checkNotNull(getDrawable(PetWidgetArtwork.badge(mood)))
            checked++
        }
        val minimumWidget=layoutInflater.inflate(R.layout.pet_widget,null,false)
        minimumWidget.measure(View.MeasureSpec.makeMeasureSpec(dp(110),View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(150),View.MeasureSpec.EXACTLY))
        minimumWidget.layout(0,0,dp(110),dp(150))
        check(minimumWidget.findViewById<View>(R.id.widget_updated).bottom<=minimumWidget.height)
        check(minimumWidget.findViewById<View>(R.id.widget_refresh).height>=dp(44))
        android.util.Log.d("PawQuestBranding","PASS minimum 110x150dp widget layout and 44dp refresh target")
        content.addView(label("2×2 widget · actual layout / synthetic data"))
        val examples=listOf("prototype_cat" to WidgetPetMood.HAPPY,"puppy" to WidgetPetMood.HUNGRY,
            "rabbit" to WidgetPetMood.THIRSTY,"capybara" to WidgetPetMood.SLEEPING)
        examples.chunked(2).forEach {pair ->
            val widgetRow=LinearLayout(this)
            pair.forEach {(species,mood)->
                val widget=layoutInflater.inflate(R.layout.pet_widget,widgetRow,false)
                widget.findViewById<ImageView>(R.id.widget_pet).setImageResource(PetWidgetArtwork.portrait(species,mood))
                widget.findViewById<ImageView>(R.id.widget_mood_icon).apply {setImageResource(PetWidgetArtwork.badge(mood));visibility=View.VISIBLE}
                widget.findViewById<TextView>(R.id.widget_name).text=when(species) {"puppy"->"Biscuit";"rabbit"->"Clover";"capybara"->"Coco";else->"Mochi"}
                widget.findViewById<TextView>(R.id.widget_mood).text=com.pawquest.localization.translate(mood.label,com.pawquest.localization.AppLanguage.ENGLISH)
                val hunger=if(mood==WidgetPetMood.HUNGRY) 18 else 80
                val water=if(mood==WidgetPetMood.THIRSTY) 20 else 85
                widget.findViewById<TextView>(R.id.widget_hunger_text).text="Food: $hunger%"
                widget.findViewById<TextView>(R.id.widget_water_text).text="Water: $water%"
                widget.findViewById<ProgressBar>(R.id.widget_hunger).progress=hunger
                widget.findViewById<ProgressBar>(R.id.widget_water).progress=water
                widget.findViewById<TextView>(R.id.widget_quests).text="2 quests left today"
                widget.findViewById<TextView>(R.id.widget_updated).text="Preview · 2×2"
                widgetRow.addView(widget,LinearLayout.LayoutParams(0,dp(160),1f).apply {setMargins(dp(3),dp(4),dp(3),dp(4))})
                widget.post {
                    check(widget.findViewById<View>(R.id.widget_updated).bottom<=widget.height) {"Widget footer clipped"}
                    check(widget.findViewById<View>(R.id.widget_refresh).height>=dp(40)) {"Widget actions too small"}
                }
            }
            content.addView(widgetRow)
        }
        content.addView(label("PASS: $checked species/mood resources · bounded bitmap sizes"))
        android.util.Log.d("PawQuestBranding","PASS $checked species/mood resources and adaptive icon masks")
    }
}
