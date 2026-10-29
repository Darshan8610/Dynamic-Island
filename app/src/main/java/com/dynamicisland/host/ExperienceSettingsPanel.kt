package com.dynamicisland.host

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dynamicisland.core.geometry.*
import com.dynamicisland.engine.settings.BehaviorOptions

@Composable
internal fun ExperienceSettingsPanel(options: BehaviorOptions, onOptions: (BehaviorOptions) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Appearance & motion",style=MaterialTheme.typography.titleLarge,color=Color.White)
        ChoiceRow("Appearance",AppearancePreset.values().toList(),options.appearance) {onOptions(options.copy(appearance=it))}
        ChoiceRow("Motion",MotionPreset.values().toList(),options.motion) {onOptions(options.copy(motion=it))}
        SettingToggle("Reduced motion",options.reducedMotion) {onOptions(options.copy(reducedMotion=it))}
        Text("All presets keep the capsule opaque black. Android’s disabled-animation setting is respected.",color=Color(0xFFB8C1BA))
        SettingToggle("Secondary activity bubble",options.secondaryBubble) {onOptions(options.copy(secondaryBubble=it))}
        Text("Optional. A separate bubble leaves the primary capsule size unchanged and keeps the gap touchable.",color=Color(0xFFB8C1BA))
        Text("Activity priority",style=MaterialTheme.typography.titleLarge,color=Color.White)
        ChoiceRow("Priority",ActivityPriority.values().toList(),options.priority) {onOptions(options.copy(priority=it))}
        Text("Calls always retain the highest priority.",color=Color(0xFFB8C1BA))
        Text("Gestures & feedback",style=MaterialTheme.typography.titleLarge,color=Color.White)
        ChoiceRow("Tap",GestureAction.values().toList(),options.tapAction) {onOptions(options.copy(tapAction=it))}
        ChoiceRow("Hold",GestureAction.values().toList(),options.holdAction) {onOptions(options.copy(holdAction=it))}
        ChoiceRow("Swipe left",GestureAction.values().toList(),options.swipeLeftAction) {onOptions(options.copy(swipeLeftAction=it))}
        ChoiceRow("Swipe right",GestureAction.values().toList(),options.swipeRightAction) {onOptions(options.copy(swipeRightAction=it))}
        Text("Media gestures act only on a visible media activity. Locked screens disable gestures.",color=Color(0xFFB8C1BA))
        Text("Haptics: ${listOf("Off","Light","Standard","Strong")[options.hapticIntensity]}",color=Color.White)
        Slider(value=options.hapticIntensity.toFloat(),onValueChange={onOptions(options.copy(hapticIntensity=it.toInt()))},valueRange=0f..3f,steps=2)
    }
}
@Composable private fun <T : Enum<T>> ChoiceRow(label: String, choices: List<T>, selected: T, onSelect:(T)->Unit) {
    Text(label,color=Color.White)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        choices.forEach { value -> FilterChip(selected=value==selected,onClick={onSelect(value)},label={Text(value.name.lowercase().replace('_',' '))}) }
    }
}
@Composable private fun SettingToggle(label:String,value:Boolean,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Text(label,color=Color.White,modifier=Modifier.weight(1f));Switch(value,change)}
}
