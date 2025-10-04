totally—here’s a generic, drop-in pattern so you can open the bottom sheet from any screen or adapter with one line. It doesn’t care if it’s Near Me, Featured, Search, etc.

1) One tiny launcher (reusable everywhere)

Create a helper to show the sheet:

// ui/savedLists/AddPlacesToList/AddPlacesToListLauncher.kt
package com.synaptix.capetowncoffees.ui.savedLists.AddPlacesToList

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

object AddPlacesToListLauncher {
fun show(fragment: Fragment, placeId: String) {
AddPlacesToListBottomSheet
.new(placeId)
.show(fragment.childFragmentManager, "add_place_to_lists")
}

    fun show(activity: FragmentActivity, placeId: String) {
        AddPlacesToListBottomSheet
            .new(placeId)
            .show(activity.supportFragmentManager, "add_place_to_lists")
    }
}

// handy extensions:
fun Fragment.showAddToLists(placeId: String) =
AddPlacesToListLauncher.show(this, placeId)

fun FragmentActivity.showAddToLists(placeId: String) =
AddPlacesToListLauncher.show(this, placeId)


Now from anywhere: showAddToLists(placeId) ✅

2) Generic adapter wiring (works for any list)
   Option A — simple lambda (most common)

Just pass (String) -> Unit and call it with the place id.

class AnyPlacesAdapter(
private val onItemClick: (PlaceLite) -> Unit = {},
private val onSaveClick: (String) -> Unit = {} // gets placeId
) : ListAdapter<PlaceLite, AnyPlacesAdapter.VH>(Diff) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val btnSave: ImageView = view.findViewById(R.id.btnSave)
        fun bind(item: PlaceLite) {
            btnSave.setOnClickListener {
                val id = item.id ?: return@setOnClickListener
                onSaveClick(id)
            }
            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    // onCreateViewHolder / onBindViewHolder … call holder.bind(item)
}


Use in any Fragment:

adapter = AnyPlacesAdapter(
onItemClick = { place -> /* open details */ },
onSaveClick  = { placeId -> showAddToLists(placeId) }
)

Option B — make the item type generic

If different lists share the same id field:

interface HasPlaceId { val id: String? }

class GenericPlacesAdapter<T : HasPlaceId>(
private val onSaveClick: (String) -> Unit
) : ListAdapter<T, GenericPlacesAdapter<T>.VH>(DiffCb<T>()) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val btnSave: ImageView = view.findViewById(R.id.btnSave)
        fun bind(item: T) {
            btnSave.setOnClickListener {
                val id = item.id ?: return@setOnClickListener
                onSaveClick(id)
            }
        }
    }
}


Then make your model implement HasPlaceId.

3) HomeFragment: one-liners for Near Me + Featured
   private fun initAdapters() {
   featuredAdapter = FeaturedAdapter(
   placesClient = placesClient,
   currentLocation = null,
   onItemClick = { place -> navigateToCafeDetails(place) },
   onSaveClick  = { place -> place.id?.let(::showAddToLists) } // ← generic
   )

   nearMeAdapter = NearMeAdapter(
   placesClient = placesClient,
   currentLocation = null,
   onItemClick = { place -> navigateToCafeDetails(place) },
   onSaveClick  = { place -> place.id?.let(::showAddToLists) } // ← generic
   )
   }


If your adapter currently expects (CoffeePlaceLite) -> Unit, keep that and just pass place.id?.let(::showAddToLists) like above.

4) If you don’t have a save button in a row yet

Add one to the row XML (e.g., item_near_me.xml or item_featured.xml):

<ImageView
android:id="@+id/btnSave"
android:layout_width="30dp"
android:layout_height="30dp"
android:background="@drawable/circle_white_bg"
android:padding="8dp"
android:src="@drawable/ic_download"
android:contentDescription="@string/save"
app:tint="@color/icon_download" />


In the ViewHolder:

holder.btnSave.setOnClickListener { onSaveClick(cafe) }