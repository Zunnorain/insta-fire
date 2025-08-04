package com.example.instafire.ui.fragments

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.extension_functions.hide
import com.example.extension_functions.setSafeOnClickListener
import com.example.extension_functions.show
import com.example.extension_functions.showToastLong
import com.example.extension_functions.showToastShort
import com.example.instafire.R
import com.example.instafire.adapters.PostsAdapter
import com.example.instafire.databinding.FragmentPostBinding
import com.example.instafire.listeners.DeleteUpdateClickListener
import com.example.instafire.listeners.PostClickListener
import com.example.instafire.models.Post
import com.example.instafire.models.User
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener
import com.karumi.dexter.listener.single.PermissionListener
import java.util.UUID

class PostFragment : Fragment() {
    private var _binding: FragmentPostBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firebaseDB: FirebaseFirestore
    private var signedInUser: User? = null

    private lateinit var postsAdapter: PostsAdapter
    private var uriContent: Uri? = null

    private lateinit var dialogImgView: ImageView

    private lateinit var pickMedia: ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentPostBinding.inflate(inflater, container, false)

        auth = FirebaseAuth.getInstance()
        firebaseDB = FirebaseFirestore.getInstance()

        getCurrentUserData()

        pickMedia =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->

                uri?.let {
                    uriContent = it
                    dialogImgView.setImageURI(uriContent)
                }
                // Use the returned uri.
            }

        //Initialize RecyclerView
        initRV()

        setupOnClickListeners()

        return binding.root
    }

    private fun setupOnClickListeners() {
        binding.run {
            addPostFab.setSafeOnClickListener {
                addNewPost()
            }
            profileBtn.setSafeOnClickListener {
                findNavController().navigate(R.id.action_postFragment_to_profileFragment)
            }
        }
    }

    private fun getCurrentUserData() {
        val userId = auth.currentUser?.uid ?: run {
            // Handle case when user is not logged in
            navigateToLogin()
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

    private fun addNewPost() {
        val builder = AlertDialog.Builder(requireContext(), android.R.style.ThemeOverlay)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.add_post_dialog, null)
        builder.setView(dialogLayout)
        builder.setCancelable(true)
        val alertDialog: AlertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.holo_blue_light)
        alertDialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(), // 90% width
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        val caption = dialogLayout.findViewById<EditText>(R.id.captionET)
        dialogImgView = dialogLayout.findViewById<ImageView>(R.id.postImgIV)
        val addPostButton = dialogLayout.findViewById<Button>(R.id.addPostBtn)

        dialogImgView.setSafeOnClickListener {
            addPhoto()
        }

        addPostButton.setSafeOnClickListener {
            val imgDescription = caption.text.toString()

            //Null Checks
            if (signedInUser == null) {
                requireContext().showToastShort("No User is signed in!")
                return@setSafeOnClickListener
            }
            if (uriContent == null) {
                requireContext().showToastShort("Please select an image to upload!")
                return@setSafeOnClickListener
            }
            if (imgDescription.isEmpty()) {
                requireContext().showToastShort("Image Caption Can't be Empty!")
                return@setSafeOnClickListener
            }

            try {
                val firebaseApp = FirebaseApp.getInstance("oldStorage")
                // 2. Get Storage service from that app
                val oldStorage = FirebaseStorage.getInstance(firebaseApp)
                val storageRef = oldStorage.reference.child("images/${UUID.randomUUID()}.jpg")

                uriContent?.let { uri ->
                    binding.progressBar.show()
                    val uploadTask = storageRef.putFile(uri)

                    uploadTask.addOnProgressListener { taskSnapshot ->
                        // Show progress bar (optional)
                        val progress =
                            (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                        binding.progressBar.progress = progress.toInt()
                    }
                        .addOnSuccessListener {
                            uploadTask.snapshot.storage.downloadUrl.addOnSuccessListener { url ->
                                binding.progressBar.hide()
                                savePostToFirebase(imgDescription, url.toString())
                                requireContext().showToastShort("Image Successfully uploaded")
                                alertDialog.dismiss()
                            }
                        }
                        .addOnFailureListener { e ->
                            binding.progressBar.hide()
                            requireContext().showToastLong("Upload failed: ${e.message}")
                        }
                }
            } catch (e: IllegalStateException) {
                binding.progressBar.hide()
                requireContext().showToastLong("Error: ${e.message}")
            }
        }
        alertDialog.show()
    }

    private fun savePostToFirebase(imgDescription: String, imageUrl: String) {

        val newPost = Post(
            description = imgDescription,
            current_time_ms = System.currentTimeMillis(),
            user = signedInUser, // Fallback if user null
            image_url = imageUrl
        )
        try {
            firebaseDB.collection("posts")
                .add(newPost)
                .addOnSuccessListener { documentRef ->
                    documentRef.update("id", documentRef.id)
                        .addOnSuccessListener {
                            requireContext().showToastShort("Post created successfully!")
                        }
                }
                .addOnFailureListener { e ->
                    requireContext().showToastShort("Error: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("FirebaseTAG", "Failure: $e")
            requireContext().showToastLong("Data failed to upload! $e")
        }
    }

    private fun addPhoto() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Dexter.withContext(requireContext())
                .withPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(object :
                    PermissionListener {
                    override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                        try {
                            pickMedia.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } catch (e: Exception) {
                            requireContext().showToastShort("Error launching image picker")
                        }
                    }

                    override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                        val dialogPermissionListener: PermissionListener =
                            DialogOnDeniedPermissionListener.Builder
                                .withContext(requireContext())
                                .withTitle("Storage Permission")
                                .withMessage("Storage permission is required to upload pictures")
                                .withButtonText(android.R.string.ok)
                                .build()
                        DialogOnDeniedPermissionListener.Builder
                            .withContext(requireContext())
                            .withTitle("Storage Permission")
                            .withMessage("Storage permission is required to upload pictures. Access settings and allow permission for the application.")
                            .withButtonText(android.R.string.ok)
                            .build()
                        if (p0 != null) {
                            if (p0.isPermanentlyDenied) {
//                                dialogPermissionListener2.onPermissionDenied(p0)
                                startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts(
                                            "package",
                                            "com.example.instafire",
                                            null
                                        )
                                    )
                                )
                            } else {
                                dialogPermissionListener.onPermissionDenied(p0)
                            }
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: PermissionRequest?, p1: PermissionToken?
                    ) {
                        p1?.continuePermissionRequest()
                    }
                }).withErrorListener {
                    requireContext().showToastShort("Error occurred!")
                }
                .check()
        } else {
            Dexter.withContext(requireContext())
                .withPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
                .withListener(object :
                    PermissionListener {
                    override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                        try {
                            pickMedia.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } catch (e: Exception) {
                            requireContext().showToastShort("Error launching image picker")
                        }
                    }

                    override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                        val dialogPermissionListener: PermissionListener =
                            DialogOnDeniedPermissionListener.Builder
                                .withContext(requireContext())
                                .withTitle("Storage Permission")
                                .withMessage("Storage permission is required to upload pictures")
                                .withButtonText(android.R.string.ok)
                                .build()
                        DialogOnDeniedPermissionListener.Builder
                            .withContext(requireContext())
                            .withTitle("Storage Permission")
                            .withMessage("Storage permission is required to upload pictures. Access settings and allow permission for the application.")
                            .withButtonText(android.R.string.ok)
                            .build()
                        if (p0 != null) {
                            if (p0.isPermanentlyDenied) {
//                                dialogPermissionListener2.onPermissionDenied(p0)
                                startActivity(Intent(Settings.ACTION_SETTINGS))
                            } else {
                                dialogPermissionListener.onPermissionDenied(p0)
                            }
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: PermissionRequest?, p1: PermissionToken?
                    ) {
                        p1?.continuePermissionRequest()
                    }
                }).withErrorListener {
                    requireContext().showToastShort("Error occurred!")
                }
                .check()
        }
    }

    private fun deletePost(postId: String) {

        // Delete the document
        firebaseDB.collection("posts").document(postId).delete()
            .addOnSuccessListener {
                // Document deleted successfully
                Log.d("Firestore", "Document successfully deleted!")
                requireContext().showToastShort("Item deleted successfully")
            }
            .addOnFailureListener { e ->
                // Error occurred while deleting
                Log.w("Firestore", "Error deleting document", e)
                requireContext().showToastShort("Error deleting item: ${e.message}")
            }
    }

    private fun initRV() {
        val currentUserID = auth.currentUser?.uid ?: "empty"
        postsAdapter = PostsAdapter(requireContext(), currentUserID, onDeleteUpdateBtnClicked(), onPostClicked())
        binding.postsRV.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = postsAdapter
            itemAnimator = DefaultItemAnimator()
        }

        //Load Data into RV
        firebaseDB.collection("posts").limit(20)
            .orderBy("current_time_ms", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null || snapshot == null) {
                    return@addSnapshotListener
                }
                val postsList = snapshot.toObjects(Post::class.java)
                postsAdapter.submitList(postsList)

                for (post in postsList) {
                    Log.d("FirebaseTAG", "Post Data = $post")
                }
            }
    }

    private fun navigateToLogin() {
        /*findNavController().navigate(R.id.loginFragment) {
            popUpTo(R.id.nav_graph) { // Replace with your graph ID
                inclusive = true
            }
        }*/
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.loginFragment, true)
            .build()

        findNavController().navigate(R.id.action_postFragment_to_loginFragment, null, navOptions)
    }

    private fun onDeleteUpdateBtnClicked(): DeleteUpdateClickListener {
        return object : DeleteUpdateClickListener {
            override fun onClick(postId: String, isDelete: Boolean) {
                if (isDelete) {
                    deletePost(postId)
                } else {
                    updatePost(postId)
                }
            }
        }
    }

    private fun onPostClicked(): PostClickListener {
        return object : PostClickListener {
            override fun onClick(postId:String) {
                val action = PostFragmentDirections.actionPostFragmentToPostDetailFragment(postId)
                findNavController().navigate(action)
            }
        }
    }

    private fun updatePost(postId: String) {
        //Show Dialog
        updateDialog(postId)
    }

    private fun updateDialog(postId: String) {
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
}