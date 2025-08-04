package com.example.instafire.ui.fragments

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.example.extension_functions.hide
import com.example.extension_functions.setSafeOnClickListener
import com.example.extension_functions.show
import com.example.extension_functions.showToastShort
import com.example.instafire.R
import com.example.instafire.databinding.FragmentProfileBinding
import com.example.instafire.firebase.notifications.FCMSender
import com.example.instafire.models.User
import com.example.instafire.workmanager.ScheduledLocalNotificationWorker
import com.example.instafire.workmanager.ScheduledNotificationWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener
import com.karumi.dexter.listener.single.PermissionListener
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firebaseDB: FirebaseFirestore
    private var signedInUser: User? = null

    private var uriContent: Uri? = null

    private lateinit var pickMedia: ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firebaseDB = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        fetchUserData()

        pickMedia =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri == null) {
                    requireContext().showToastShort("No image was selected")
                    binding.progressBar.hide()
                    return@registerForActivityResult
                }
                binding.progressBar.show()

                uriContent = uri
                binding.profilePicIV.setImageURI(uri)

                val firebaseApp = FirebaseApp.getInstance("oldStorage")
                // 2. Get Storage service from that app
                val oldStorage = FirebaseStorage.getInstance(firebaseApp)
                val storageRef = oldStorage.reference.child("profilePictures/")
                val fileReference = storageRef.child(
                    auth.currentUser?.uid + "/profilePicture."
                            + getFileExtension(uriContent!!)
                )

                //Upload Image to Firebase Storage
                fileReference.putFile(uriContent!!).addOnSuccessListener {
                    binding.progressBar.hide()
                    requireContext().showToastShort("Upload Successful")

                }.addOnFailureListener { e ->
                    requireContext().showToastShort("${e.message}")
                }


            }

        setUpOnClickListeners()

        return binding.root
    }

    private fun setUpOnClickListeners() {
        binding.run {

            profilePicIV.setSafeOnClickListener {
                addPhoto()
            }

            logOutBtn.setSafeOnClickListener {
                logOut()
            }

            //Local Notifs
            val hasNotificationPermission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            sendNotificationBtn.setSafeOnClickListener {
                if (hasNotificationPermission) {
                    showLocalNotification()
                } else {
                    requestPermissionNotification()
                }
            }
            localAppointmentBtn.setSafeOnClickListener {
                if (hasNotificationPermission) {
                    scheduleLocalAppointment(18,12)
                } else {
                    requestPermissionNotification()
                }
            }
            dailyAppointmentBtn.setSafeOnClickListener {
                if (hasNotificationPermission) {
                    dailyLocalReminderNotification(6, 10)
                } else {
                    requestPermissionNotification()
                }
            }

            //Firebase Notifs
            sendFCMNotifBtn.setSafeOnClickListener {
                sendFirebaseNotificationToAll()
            }
            fcmAppointmentBtn.setSafeOnClickListener {
                scheduleFirebaseAppointment(17,55)
            }

        }
    }

    private fun dailyLocalReminderNotification(hour: Int, minute: Int){
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND,0)

            if(timeInMillis <= currentDate.timeInMillis){
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ScheduledLocalNotificationWorker>(
            repeatInterval = 24, // Hours
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 15, // Minutes
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "daily_appointment_reminder",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun scheduleLocalAppointment(hour: Int, minute: Int){
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND,0)

            if(timeInMillis <= currentDate.timeInMillis){
                add(Calendar.DAY_OF_MONTH, 1)
            }


        }

        //We do duedate because due date time in millis is larger than currentdate time in mill
        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

        val notificationWorker = OneTimeWorkRequestBuilder <ScheduledLocalNotificationWorker>()
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(requireContext()).enqueue(notificationWorker)

    }

    private fun scheduleFirebaseAppointment(hour:Int, minute:Int){
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND,0)

            if(timeInMillis <= currentDate.timeInMillis){
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        //We do duedate because due date time in millis is larger than currentdate time in mill
        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

        val notificationWorker = OneTimeWorkRequestBuilder <ScheduledNotificationWorker>()
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(requireContext()).enqueue(notificationWorker)
    }

    private fun sendFirebaseNotificationToAll() {
        lifecycleScope.launch {
            try {
                val sender = FCMSender(requireContext())
                val success = sender.sendNotificationToTopic(
                    title = "Hello World!",
                    body = "This is to remind you to NEVER negotiate with your goals!",
                )
                if (success) {
                    requireContext().showToastShort("Notification sent!")
                } else {
                    requireContext().showToastShort("Failed to send")
                }

            } catch (e: Exception) {
                requireContext().showToastShort("Error: ${e.message}")
                Log.e("Notification", "Error sending notification", e)
            }
        }
    }

    private fun requestPermissionNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Dexter.withContext(requireContext())
                .withPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                .withListener(object :
                    PermissionListener {
                    override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                        requireContext().showToastShort("Good job! Permissin granted")
                    }

                    override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                        val dialogPermissionListener: PermissionListener =
                            DialogOnDeniedPermissionListener.Builder
                                .withContext(requireContext())
                                .withTitle("Notification Permission")
                                .withMessage("Notification permission is required to send Notifications")
                                .withButtonText(android.R.string.ok)
                                .build()
                        DialogOnDeniedPermissionListener.Builder
                            .withContext(requireContext())
                            .withTitle("Notification Permission")
                            .withMessage("Notification permission is required to send Notifs. Access settings and allow permission for the application.")
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
        }
    }

    private fun showLocalNotification() {
        val notifManager =
            requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification =
            NotificationCompat.Builder(requireContext().applicationContext, "channel_id")
                .setSmallIcon(R.drawable.unsaved_icon)
                .setContentTitle("Hello World!")
                .setContentText("Hello this is my first notif, not really but yeah!")
                .build()

        notifManager.notify(1, notification)
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

    private fun fetchUserData() {
        val userId = auth.currentUser?.uid ?: run {
            // Handle case when user is not logged in
            navigateToLogin()
            return
        }
        // Get User Data from FireStore
        firebaseDB.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { userSnapShot ->
                signedInUser = userSnapShot.toObject(User::class.java)
                binding.welcomeTV.text = "Welcome ${signedInUser?.name}"

                // Load profile picture after getting user data
                loadProfilePicture(userId)

                binding.progressBar.hide()
            }
            .addOnFailureListener { exception ->
                requireContext().showToastShort("Failure fetching User: ${exception.message}")
                binding.progressBar.hide()
            }

    }

    private fun loadProfilePicture(userId: String) {
        //Get User Profile Pics from Firebase Storage
        val firebaseApp = FirebaseApp.getInstance("oldStorage")
        // 2. Get Storage service from that app
        val oldStorage = FirebaseStorage.getInstance(firebaseApp)
        val storageRef =
            oldStorage.reference.child("profilePictures/$userId/profilePicture.jpg")

        storageRef.downloadUrl.addOnSuccessListener { profilePicUri ->
//            binding.profilePicIV.setImageURI(profilePicUri)
            Glide.with(requireContext()).load(profilePicUri)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_launcher_background)
                .into(binding.profilePicIV)
        }.addOnFailureListener {
            requireContext().showToastShort("Failed to Load Profile Pic")
        }
    }

    private fun getFileExtension(uri: Uri): String? {
        val contentRes = requireContext().contentResolver
        val mime = MimeTypeMap.getSingleton()
        return mime.getExtensionFromMimeType(contentRes.getType(uri))
    }

    private fun logOut() {
        auth.signOut()
        /*findNavController().navigate(R.id.action_profileFragment_to_loginFragment) {
            popUpTo(R.id.nav_graph) { // Replace with your graph ID
                inclusive = true
            }
        }*/
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.loginFragment, true)
            .build()

        findNavController().navigate(R.id.action_profileFragment_to_loginFragment, null, navOptions)
    }


}