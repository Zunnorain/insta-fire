package com.example.instafire.adapters

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.extension_functions.hide
import com.example.extension_functions.setSafeOnClickListener
import com.example.extension_functions.show
import com.example.instafire.databinding.CommentLayoutBinding
import com.example.instafire.databinding.PostLayoutBinding
import com.example.instafire.listeners.DeleteUpdateCommentListener
import com.example.instafire.listeners.PostClickListener
import com.example.instafire.models.Comment
import com.example.instafire.models.Post
import com.example.instafire.utils.showSnackMessage

class CommentsAdapter(
    private val context: Context,
    private val currentUser: String,
    private val deleteUpdateCommentListener: DeleteUpdateCommentListener,
) :
    RecyclerView.Adapter<CommentsAdapter.MyViewHolder>() {

    //Diff Util Class callback method; it improves on the recyclerview and loads items faster
    private val diffCallback = object : DiffUtil.ItemCallback<Comment>() {

        //Checking whether the unique id's of the items in the list are the same as the new list
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem.id == newItem.id
        }

        //Checking whether all the items in the list are the same as the new list
        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem == newItem
        }
    }

    //Required for getting an adapter to pass item updates to and a callback for comparing new and old list
    private val differ = AsyncListDiffer(this, diffCallback)

    fun submitList(list: List<Comment>) {
        differ.submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        return MyViewHolder(
            CommentLayoutBinding.inflate(
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
        val commentList = differ.currentList[position]

        holder.binding.commentUserName.text = "Comment by:${commentList.user?.name}"
        holder.binding.commentDescription.text = commentList.content
//        holder.binding.postTime.text = postList.current_time_ms.toTimeAgo(context)
        holder.binding.commentTime.text = DateUtils.getRelativeTimeSpanString(commentList.current_time_ms)

        if (commentList.user?.user_id == currentUser) {
            holder.binding.deleteCommentIV.show()
        } else {
            holder.binding.deleteCommentIV.hide()
        }

        if (commentList.user?.user_id == currentUser) {
            holder.binding.updateCommentIV.show()
        } else {
            holder.binding.updateCommentIV.hide()
        }

        holder.binding.deleteCommentIV.setSafeOnClickListener {
            deleteUpdateCommentListener.onClick(commentList.id, true)
        }

        holder.binding.updateCommentIV.setSafeOnClickListener {
            deleteUpdateCommentListener.onClick(commentList.id, false)
        }

    }

    class MyViewHolder(val binding: CommentLayoutBinding) :
        RecyclerView.ViewHolder(binding.root)

}