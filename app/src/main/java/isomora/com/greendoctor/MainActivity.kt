package isomora.com.greendoctor

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.annotation.RequiresApi
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.widget.ImageView
import android.widget.Toast
import com.itextpdf.text.*
import com.itextpdf.text.BaseColor
import com.itextpdf.text.pdf.PdfWriter
import kotlinx.android.synthetic.main.activity_main.*
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var mClassifier: Classifier
    private lateinit var mBitmap: Bitmap
    private val mCameraRequestCode = 0
    private val mGalleryRequestCode = 2
    private val STORAGE_CODE: Int = 100
    private val mInputSize = 48
    private val mModelPath = "model.tflite"
    private val mLabelPath = "labels.txt"
    private val mSamplePath = "benign.png"


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(R.layout.activity_main)
        mClassifier = Classifier(assets, mModelPath, mLabelPath, mInputSize)

        resources.assets.open(mSamplePath).use {
            mBitmap = BitmapFactory.decodeStream(it)
            mPhotoImageView.setImageBitmap(mBitmap)
        }


        mGalleryButton.setOnClickListener {
            val callGalleryIntent = Intent(Intent.ACTION_PICK)
            callGalleryIntent.type = "image/*"
            startActivityForResult(callGalleryIntent, mGalleryRequestCode)
        }
        mDetectButton.setOnClickListener {
            val chunkedImages: ArrayList<Bitmap> = ArrayList<Bitmap>(scaleImage(mBitmap))
            var c1=0
            var c2=0
            var c=0.0
            for (i in 0..chunkedImages.size-1)
            {
                val results = mClassifier.recognizeImage(chunkedImages.get(i)).firstOrNull()
                var d=results?.confidence
                if(results?.title=="Benign"&&d!!.toFloat()>=0.6)
                {
                    c1++
                    c+=d!!.toFloat()
                }
                else if(results?.title=="Malignant"&&d!!.toFloat()>=0.6)
                {
                    c2++
                    c+=1-d!!.toFloat()
                }
            }
            c1/=chunkedImages.size
            c2/=chunkedImages.size
            c/=chunkedImages.size
            if (c1>0.5)
            {
                mResultTextView.text= "Benign"+"\n Confidence:"+c+"\n"+c1+"\n"+chunkedImages.size
            }
            else if(c2>0.5)
            {
                mResultTextView.text= "Malignant"+"\n Confidence:"+(1-c)+"\n"+c2+"\n"+chunkedImages.size
            }
            else
            {
                mResultTextView.text="Not Histopathological Image"
            }
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M){
                //system OS >= Marshmallow(6.0), check permission is enabled or not
                if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_DENIED){
                    //permission was not granted, request it
                    val permissions = arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    requestPermissions(permissions, STORAGE_CODE)
                }
                else{
                    //permission already granted, call savePdf() method
                    savePdf()
                }
            }
            else{
                //system OS < marshmallow, call savePdf() method
                savePdf()
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode == mCameraRequestCode){
            //Considérons le cas de la caméra annulée
            if(resultCode == Activity.RESULT_OK && data != null) {
                mBitmap = data.extras!!.get("data") as Bitmap
                val toast = Toast.makeText(this, ("Image crop to: w= ${mBitmap.width} h= ${mBitmap.height}"), Toast.LENGTH_LONG)
                toast.setGravity(Gravity.BOTTOM, 0, 20)
                toast.show()
                mPhotoImageView.setImageBitmap(mBitmap)
                mResultTextView.text= "Your photo image set now."
            } else {
                Toast.makeText(this, "Camera cancel..", Toast.LENGTH_LONG).show()
            }
        } else if(requestCode == mGalleryRequestCode) {
            if (data != null) {
                val uri = data.data

                try {
                    mBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                println("Success!!!")
                //Bitmap xBitmap = scaleImage(mBitmap)
                val orignalWidth = mBitmap!!.width
                val originalHeight = mBitmap.height
                val scaleWidth = mInputSize.toFloat() / orignalWidth
                val scaleHeight = mInputSize.toFloat() / originalHeight
                val matrix = Matrix()
                matrix.postScale(scaleWidth, scaleHeight)
                mBitmap=Bitmap.createBitmap(mBitmap, 0, 0, orignalWidth, originalHeight)
                mPhotoImageView.setImageBitmap(mBitmap)

            }
        } else {
            Toast.makeText(this, "Unrecognized request code", Toast.LENGTH_LONG).show()

        }
    }


    fun scaleImage(bitmap: Bitmap?): ArrayList<Bitmap> {
        val orignalWidth = bitmap!!.width
        val originalHeight = bitmap.height
        val scaleWidth = mInputSize.toFloat() / orignalWidth
        val scaleHeight = mInputSize.toFloat() / originalHeight
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        val chunkedImages: ArrayList<Bitmap> = ArrayList<Bitmap>((originalHeight/mInputSize)*(orignalWidth/mInputSize))
        var yCoord = 0
        for (x in 0 until originalHeight/mInputSize) {
            var xCoord = 0
            for (y in 0 until orignalWidth/mInputSize) {
                chunkedImages.add(Bitmap.createBitmap(bitmap, xCoord, yCoord, mInputSize, mInputSize))
                xCoord += mInputSize
            }
            yCoord += mInputSize
        }
        return chunkedImages
    }
    private fun savePdf() {
        //create object of Document class
        val mDoc = Document()
        //pdf file name
        val mFileName = editText1.text.toString()
        //pdf file path
        val mFilePath = Environment.getExternalStorageDirectory().toString() + "/" + mFileName +".pdf"
        try {
            //create instance of PdfWriter class
            PdfWriter.getInstance(mDoc, FileOutputStream(mFilePath))

            //open the document for writing
            mDoc.open()

            //add author of the document (metadata)
            mDoc.addAuthor("Breast Cancer Classifier")
            val ims: InputStream = assets.open("applogo.png")
            val bmp = BitmapFactory.decodeStream(ims)
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val image =
                Image.getInstance(stream.toByteArray())
            image.scaleAbsolute(100f, 100f)
            image.setAlignment(Element.ALIGN_CENTER)
            val customcolor1 = BaseColor(107,224,141)
            val f1 = Font(Font.FontFamily.TIMES_ROMAN, 20.0f, Font.BOLD, customcolor1)
            val Head1 = Paragraph("Breast Cancer Classifier", f1)
            Head1.setAlignment(Element.ALIGN_CENTER)
            val f2 = Font(Font.FontFamily.COURIER, 30.0f, Font.BOLD, BaseColor.BLACK)
            val f3 = Font(Font.FontFamily.COURIER, 10.0f, Font.BOLD, BaseColor.BLACK)
            val Head2 = Paragraph("REPORT", f2)
            Head2.setAlignment(Element.ALIGN_CENTER)
            val Date=Paragraph(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(System.currentTimeMillis()))
            Date.setAlignment(Element.ALIGN_RIGHT)
            val mText1 = Paragraph("Patient's Name  "+editText1.text.toString())
            val mText2 = Paragraph("Gender              "+editText2.selectedItem.toString())
            val mText3 = Paragraph("Age                    "+editText3.text.toString())
            val mText4 = Paragraph("Contact No        "+editText4.text.toString())
            val imageView: ImageView = findViewById(R.id.mPhotoImageView) as ImageView
            val bitmap = (imageView.getDrawable() as BitmapDrawable).bitmap
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val image2 =
                Image.getInstance(baos.toByteArray())
            image2.scaleAbsolute(350f, 350f)
            image2.setAlignment(Element.ALIGN_CENTER)
            val mText5 = Paragraph(mResultTextView.text.toString(),f3)
            mText5.setAlignment(Element.ALIGN_CENTER)
            mDoc.add(image)

            //add paragraph to the document
            mDoc.add(Head1)
            mDoc.add(Head2)
            mDoc.add( Chunk.NEWLINE )
            mDoc.add(Date)
            mDoc.add(mText1)
            mDoc.add(mText2)
            mDoc.add(mText3)
            mDoc.add(mText4)
            mDoc.add( Chunk.NEWLINE )
            mDoc.add(image2)
            mDoc.add(mText5)
            //close document
            mDoc.close()

            //show file saved message with file name and path
            Toast.makeText(this, "$mFileName.pdf\nis saved to\n$mFilePath", Toast.LENGTH_SHORT).show()
        }
        catch (e: Exception){
            //if anything goes wrong causing exception, get and show exception message
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode){
            STORAGE_CODE -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    //permission from popup was granted, call savePdf() method
                    savePdf()
                }
                else{
                    //permission from popup was denied, show error message
                    Toast.makeText(this, "Permission denied...!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}


