package com.example.instafire.ui.fragments

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.extension_functions.hide
import com.example.extension_functions.setSafeOnClickListener
import com.example.extension_functions.show
import com.example.extension_functions.showToastShort
import com.example.instafire.R
import com.example.instafire.adapters.CommentsAdapter
import com.example.instafire.databinding.FragmentPostDetailBinding
import com.example.instafire.listeners.DeleteUpdateCommentListener
import com.example.instafire.models.Comment
import com.example.instafire.models.Post
import com.example.instafire.models.User
import com.example.instafire.utils.toTimeAgo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class PostDetailFragment : Fragment() {
    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firebaseDB: FirebaseFirestore
    private lateinit var commentRef: DocumentReference
    private var signedInUser: User? = null

    private lateinit var commentsAdapter: CommentsAdapter
    private var postId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firebaseDB = FirebaseFirestore.getInstance()
        commentRef = firebaseDB.collection("comments").document()

        postId = arguments?.getString("postId")

        // Verify we got the ID
        if (postId.isNullOrEmpty()) {
            requireContext().showToastShort("Post ID not found")
            findNavController().popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentPostDetailBinding.inflate(inflater, container, false)

        postId?.let { id ->
            // Load post details using the ID
            getPostData(id)
        }

        getCurrentUserData()

        initRV()

        setupOnClickListeners()

        return binding.root
    }

    private fun getCurrentUserData() {
        val userId = auth.currentUser?.uid ?: run {
            // Handle case when user is not logged in
            return
        }

        firebaseDB.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { userSnapShot ->
                signedInUser = userSnapShot.toObject(User::class.java)
            }
            .addOnFailureListener { exception ->
                requireContext().showToastShort("Failure fetching User: ${exception.message}")
            }
    }

    private fun initRV() {
        val userId = auth.currentUser?.uid ?: run {
            // Handle case when user is not logged in
            return
        }

        commentsAdapter = CommentsAdapter(requireContext(), userId, onDeleteUpdateBtnClicked())

        binding.commentsRV.apply {
            adapter = commentsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        //Load Data into RV
        firebaseDB.collection("comments").limit(20)
            .orderBy("current_time_ms", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null || snapshot == null) {
                    return@addSnapshotListener
                }
                val commentList = snapshot.toObjects(Comment::class.java)
                commentsAdapter.submitList(commentList)

                for (comment in commentList) {
                    Log.d("FirebaseTAG", "comment Data = $comment")
                }
            }
    }

    private fun setupOnClickListeners() {
        binding.run {
            updatePostIV.setSafeOnClickListener {
                postId?.let { updatePost(it) }
            }

            deleteIV.setSafeOnClickListener {
                postId?.let { deletePost(it) }
            }

            addCommentBtn.setSafeOnClickListener {

                addNewComment()

            }
        }
    }

    private fun addNewComment() {

        val newComment = binding.commentET.text.toString()

        //Null Checks
        if (signedInUser == null) {
            requireContext().showToastShort("No User is signed in!")
            return
        }

        if (newComment.isEmpty()) {
            requireContext().showToastShort("Image Caption Can't be Empty!")
            return
        }
        if (postId.isNullOrEmpty()) {
            requireContext().showToastShort("No valid Post exists!")
            return
        }
        try {
            val commentData = Comment(
                postId = postId!!,
                content = newComment,
                current_time_ms = System.currentTimeMillis(),
                user = signedInUser
            )
            firebaseDB.collection("comments")
                .add(commentData)
                .addOnSuccessListener { documentRef ->
                    documentRef.update("id", documentRef.id)
                        .addOnSuccessListener {
                            requireContext().showToastShort("Comment posted successfully!")
                        }
                }.addOnFailureListener { e ->
                    requireContext().showToastShort("Error: ${e.message}")
                }

        } catch (e: Exception) {
            requireContext().showToastShort("Error mate: ${e.message}")
        }
    }

    private fun onDeleteUpdateBtnClicked(): DeleteUpdateCommentListener {
        return object : DeleteUpdateCommentListener {
            override fun onClick(commentId: String, isDelete: Boolean) {
                if (isDelete) {
                    deleteComment(commentId)
                } else {
                    updateComment(commentId)
                }
            }
        }
    }

    //Posts
    private fun getPostData(postId: String) {
        val userId = auth.currentUser?.uid ?: run {
            // Handle case when user is not logged in
            return
        }

        binding.run {
            firebaseDB.collection("posts").document(postId)
                .addSnapshotListener { postSnapshot, event ->
                    val post = postSnapshot?.toObject(Post::class.java)
                    Glide.with(requireContext()).load(post?.image_url).into(postImageIV)
                    postUserName.text = "Posted by:${post?.user?.name}"
                    postDescription.text = "Caption: ${post?.description}"
                    postTime.text = post?.current_time_ms?.toTimeAgo(requireContext())

                    if (post?.user?.user_id == userId) {
                        deleteIV.show()
                    } else {
                        deleteIV.hide()
                    }

                    if (post?.user?.user_id == userId) {
                        updatePostIV.show()
                    } else {
                        updatePostIV.hide()
                    }

                    deleteIV.setSafeOnClickListener {
//                        deleteUpdateClickListener.onClick(postList.id, true)
                        deletePost(postId)
                    }

                    updatePostIV.setSafeOnClickListener {
//                        deleteUpdateClickListener.onClick(postList.id, false)
                        updatePost(postId)
                    }

                }
        }
    }

    private fun deletePost(postId: String) {
        // Delete the document
        firebaseDB.collection("posts").document(postId).delete()
            .addOnSuccessListener {
                // Document deleted successfully
                Log.d("Firestore", "Document successfully deleted!")
                requireContext().showToastShort("Item deleted successfully")
                findNavController().popBackStack()
            }
            .addOnFailureListener { e ->
                // Error occurred while deleting
                Log.w("Firestore", "Error deleting document", e)
                requireContext().showToastShort("Error deleting item: ${e.message}")
            }
    }

    private fun updatePost(postId: String) {
        //Show Dialog
        updatePostDialog(postId)
    }

    private fun updatePostDialog(postId: String) {
        val builder = AlertDialog.Builder(requireActivity())
        val dialogLayout =
            requireActivity().layoutInflater.inflate(R.layout.update_post_dialog_layout, null)
        builder.setView(dialogLayout)
        val alertDialog: AlertDialog = builder.create()
        alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        alertDialog.window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val caption = dialogLayout.findViewById<EditText>(R.id.updateCaptionET)
        val yesButton = dialogLayout.findViewById<RelativeLayout>(R.id.yesRelative)

        firebaseDB.collection("posts").document(postId).get().addOnSuccessListener { postSnapShot ->
            val post = postSnapShot.toObject(Post::class.java)
            caption.setText(post?.description ?: "empty")
        }

        yesButton.setOnClickListener {
            val updatedCaption = caption.text.toString()

            if (updatedCaption.isEmpty()) {
                requireContext().showToastShort("Please enter a caption!")
                return@setOnClickListener
            }

            firebaseDB.collection("posts").document(postId).update("description", updatedCaption)
                .addOnSuccessListener {
                    // Update successful
                    requireContext().showToastShort("Description updated")
                }
                .addOnFailureListener { e ->
                    // Handle error
                    requireContext().showToastShort("Update failed: ${e.message}")
                }
            alertDialog.dismiss()
        }
        alertDialog.show()
    }

    //Comments
    private fun deleteComment(commentId: String) {
        // Delete the document
        firebaseDB.collection("comments").document(commentId).delete()
            .addOnSuccessListener {
                // Document deleted successfully
                Log.d("Firestore", "Document successfully deleted!")
                requireContext().showToastShort("Comment deleted successfully")
            }
            .addOnFailureListener { e ->
                // Error occurred while deleting
                Log.w("Firestore", "Error deleting document", e)
                requireContext().showToastShort("Error deleting comment: ${e.message}")
            }
    }

    private fun updateComment(commentId: String) {
        //Show Dialog
        updateCommentDialog(commentId)
    }

    private fun updateCommentDialog(commentId: String) {
        val builder = AlertDialog.Builder(requireActivity())
        val dialogLayout =
            requireActivity().layoutInflater.inflate(R.layout.update_post_dialog_layout, null)
        builder.setView(dialogLayout)
        val alertDialog: AlertDialog = builder.create()
        alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        alertDialog.window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val content = dialogLayout.findViewById<EditText>(R.id.updateCaptionET)
        val yesButton = dialogLayout.findViewById<RelativeLayout>(R.id.yesRelative)
        val titleText = dialogLayout.findViewById<TextView>(R.id.exitTV)

        titleText.text = "Update your comment"

        //Retrieve the content of the Comment being updated
        firebaseDB.collection("comments").document(commentId).get()
            .addOnSuccessListener { commentSnapShot ->
                val comment = commentSnapShot.toObject(Comment::class.java)
                content.setText(comment?.content ?: "empty")
            }

        yesButton.setOnClickListener {
            val updatedContent = content.text.toString()

            if (updatedContent.isEmpty()) {
                requireContext().showToastShort("Please enter a comment!")
                return@setOnClickListener
            }

            firebaseDB.collection("comments").document(commentId).update("content", updatedContent)
                .addOnSuccessListener {
                    // Update successful
                    requireContext().showToastShort("Comment updated")
                }
                .addOnFailureListener { e ->
                    // Handle error
                    requireContext().showToastShort("Update failed: ${e.message}")
                }
            alertDialog.dismiss()
        }
        alertDialog.show()
    }
}