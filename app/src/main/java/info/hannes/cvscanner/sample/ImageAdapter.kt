package info.hannes.cvscanner.sample

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import info.hannes.cvscanner.sample.Utility.deleteFilePermanently
import info.hannes.cvscanner.util.Util
import java.io.IOException

class ImageAdapter : RecyclerView.Adapter<ImageViewHolder?>() {
    private val imageUris: MutableList<Uri> = mutableListOf()

    /**
     * Called when RecyclerView needs a new [RecyclerView.ViewHolder] of the given type to represent
     * an item.
     * 
     * 
     * This new ViewHolder should be constructed with a new View that can represent the items
     * of the given type. You can either create a new View manually or inflate it from an XML
     * layout file.
     * 
     * 
     * The new ViewHolder will be used to display items of the adapter using
     * different items in the data set, it is a good idea to cache references to sub views of
     * the View to avoid unnecessary [View.findViewById] calls.
     * 
     * @param parent   The ViewGroup into which the new View will be added after it is bound to
     * an adapter position.
     * @param viewType The view type of the new View.
     * @return A new ViewHolder that holds a View of the given view type.
     * @see .getItemViewType
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = ImageView(parent.getContext())
        val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        view.setLayoutParams(params)
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE)
        view.setBackgroundColor(Color.TRANSPARENT)
        view.setPadding(8, 8, 8, 8)
        return ImageViewHolder(view)
    }

    /**
     * Called by RecyclerView to display the data at the specified position. This method should
     * update the contents of the [RecyclerView.ViewHolder.itemView] to reflect the item at the given
     * position.
     * 
     * 
     * Note that unlike [ListView], RecyclerView will not call this method
     * again if the position of the item changes in the data set unless the item itself is
     * invalidated or the new position cannot be determined. For this reason, you should only
     * use the `position` parameter while acquiring the related data item inside
     * this method and should not keep a copy of it. If you need the position of an item later
     * on (e.g. in a click listener), use [RecyclerView.ViewHolder.getAdapterPosition] which will
     * have the updated adapter position.
     * 
     * 
     * handle efficient partial bind.
     * 
     * @param holder   The ViewHolder which should be updated to represent the contents of the
     * item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        if (position < imageUris.size) {
            val imageUri = imageUris.get(position)

            Log.d("ADAPTER", "position: " + position + ", uri: " + imageUri)

            val context = holder.view!!.getContext()
            var image: Bitmap? = null

            try {
                val scale = Util.calculateInSampleSize(
                    context, imageUri, holder.view!!.getWidth(),
                    holder.view!!.getHeight(), true
                )
                image = Util.loadBitmapFromUri(context, scale, imageUri)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            Log.d("ADAPTER", "decoded image: " + (image != null))
            holder.view!!.setImageBitmap(image)
        }
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     * 
     * @return The total number of items in this adapter.
     */
    override fun getItemCount(): Int {
        return imageUris.size
    }

    fun add(imageUri: Uri) {
        val pos = imageUris.size
        imageUris.add(imageUri)
        notifyItemInserted(pos)
        Log.d("ADAPTER", "added image")
    }

    fun clear() {
        if (!imageUris.isEmpty()) {
            val paths = imageUris.map { it.path!! }.toMutableList()
            imageUris.clear()
            notifyDataSetChanged()

            for (path in paths) {
                deleteFilePermanently(path)
            }
        }

        Log.d("ADAPTER", "cleared all images")
    }
}

class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var view: ImageView? = null

    init {
        if (itemView is ImageView) {
            this.view = itemView
        }
    }
}
