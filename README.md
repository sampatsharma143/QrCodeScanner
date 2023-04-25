# QrCodeScanner

This library Helps you implement qr code scanner in your app 
#How to use it 


# add dependencies 
    implementation "androidx.camera:camera-camera2:1.3.0-alpha06"
    implementation "androidx.camera:camera-lifecycle:1.3.0-alpha06"
    implementation "androidx.camera:camera-view:1.3.0-alpha06"
    implementation 'com.github.sampatsharma143:QrCodeScanner:v1.0.0'


add PreviewView in your xml layout file 

```
    <androidx.camera.view.PreviewView
        android:id="@+id/scanPreview"
        android:layout_width="300dp"
        android:layout_height="300dp"
        android:layout_margin="32dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"/>
```
## MainActivity File 

```
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.shunyank.mlqrcode.QRScanner
import com.shunyank.mlqrcode.QrCodeActivity
import com.shunyank.mlqrcode.ScanListener

class MainActivity : AppCompatActivity() {
    private lateinit var listener: ScanListener
    private val PERMISSION_CAMERA_REQUEST = 1
     var  qrScanner : QRScanner? =null
    lateinit var preview:PreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preview = findViewById(R.id.scanPreview)

         listener =object : ScanListener {

            override fun onScanned(code: String) {
                Log.e("code",code)
            }

        }

        if (isCameraPermissionGranted()) {
            qrScanner = QRScanner(this,preview,listener)
            qrScanner?.setLifeCycle(this)

        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(
                    arrayOf(Manifest.permission.CAMERA),
                    PERMISSION_CAMERA_REQUEST
                )
            }
        }
//        startActivity(Intent(this, QrCodeActivity::class.java))
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_CAMERA_REQUEST) {
            if (isCameraPermissionGranted()) {
//                setupCamera()
                qrScanner = QRScanner(this,preview,listener)
                qrScanner?.setLifeCycle(this)
                qrScanner?.SetupCamera(scanListener = listener)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(
                        arrayOf(Manifest.permission.CAMERA),
                        PERMISSION_CAMERA_REQUEST
                    )
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun isCameraPermissionGranted(): Boolean = this.let {
        ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA)
    } == PackageManager.PERMISSION_GRANTED
}
```
