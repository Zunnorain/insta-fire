package com.example.instafire.adapters

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.extension_functions.hide
import com.example.extension_functions.setSafeOnClickListener
import com.example.extension_functions.show
import com.example.instafire.databinding.PostLayoutBinding
import com.example.instafire.listeners.DeleteUpdateClickListener
import com.example.instafire.listeners.PostClickListener
import com.example.instafire.models.Post
import com.example.instafire.utils.showSnackMessage

class PostsAdapter(
    private val context: Context,
    private val currentUser: String,
    private val deleteUpdateClickListener: DeleteUpdateClickListener,
    private val postClickListener: PostClickListener
) :
    RecyclerView.Adapter<PostsAdapter.MyViewHolder>() {

    //Diff Util Class callback method; it improves on the recyclerview and loads items faster
    private val diffCallback = object : DiffUtil.ItemCallback<Post>() {

        //Checking whether the unique id's of the items in the list are the same as the new list
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem.id == newItem.id
        }

        //Checking whether all the items in the list are the same as the new list
        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem == newItem
        }
    }

    //Required for getting an adapter to pass item updates to and a callback for comparing new and old list
    private val differ = AsyncListDiffer(this, diffCallback)

    fun submitList(list: List<Post>) {
        differ.submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        return MyViewHolder(
            PostLayoutBinding.inflate(
                LayoutInflater.from(context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val postList = differ.currentList[position]

        Glide.with(context).load(postList.image_url).into(holder.binding.postImageIV)
        holder.binding.postUserName.text = "Posted by:${postList.user?.name}"
        holder.binding.postDescription.text = "Caption: ${postList.description}"
//        holder.binding.postTime.text = postList.current_time_ms.toTimeAgo(context)
        holder.binding.postTime.text = DateUtils.getRelativeTimeSpanString(postList.current_time_ms)

        if (postList.user?.user_id == currentUser) {
            holder.binding.deleteIV.show()
        } else {
            holder.binding.deleteIV.hide()
        }

        if (postList.user?.user_id == currentUser) {
            holder.binding.updatePostIV.show()
        } else {
            holder.binding.updatePostIV.hide()
        }

        holder.binding.deleteIV.setSafeOnClickListener {
//            deletePost.invoke(postList.id)
            deleteUpdateClickListener.onClick(postList.id, true)
        }

        holder.binding.updatePostIV.setSafeOnClickListener {
            deleteUpdateClickListener.onClick(postList.id, false)
        }

        //handle post clicks
        holder.binding.root.setSafeOnClickListener {
            postClickListener.onClick(postList.id)
        }
    }

    class MyViewHolder(val binding: PostLayoutBinding) :
        RecyclerView.ViewHolder(binding.root)

}