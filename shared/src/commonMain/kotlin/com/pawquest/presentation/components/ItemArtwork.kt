package com.pawquest.presentation.components
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import com.pawquest.resources.*
@Composable fun ItemArtwork(assetId: String,imageVector: ImageVector,contentDescription: String?,tint: Color,modifier: Modifier=Modifier) {
 val art=when(assetId) {
  "acc-crown"->Res.drawable.decor_acc_crown
  "acc-wizard-hat"->Res.drawable.decor_acc_wizard_hat
  "acc-sprout"->Res.drawable.decor_acc_sprout
  "acc-headphones"->Res.drawable.decor_acc_headphones
  "acc-glasses"->Res.drawable.decor_acc_glasses
  "acc-monocle"->Res.drawable.decor_acc_monocle
  "acc-bowtie"->Res.drawable.decor_acc_bowtie
  "acc-scarf"->Res.drawable.decor_acc_scarf
  "acc-wings"->Res.drawable.decor_acc_wings
  "acc-backpack"->Res.drawable.decor_acc_backpack
  "furn-window"->Res.drawable.decor_furn_window
  "furn-clock"->Res.drawable.decor_furn_clock
  "furn-lights"->Res.drawable.decor_furn_lights
  "furn-bookshelf"->Res.drawable.decor_furn_bookshelf
  "furn-cat-tree"->Res.drawable.decor_furn_cat_tree
  "furn-bed"->Res.drawable.decor_furn_bed
  "furn-plant"->Res.drawable.decor_furn_plant
  "furn-tea-table"->Res.drawable.decor_furn_tea_table
  "furn-gramophone"->Res.drawable.decor_furn_gramophone
  "furn-lamp"->Res.drawable.decor_furn_lamp
  else->null
 }
 if(art!=null) Image(painterResource(art),contentDescription,modifier,contentScale=ContentScale.Fit)
 else Icon(imageVector,contentDescription,modifier,tint=tint)
}
