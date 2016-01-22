package com.egroat.openlens;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import com.itextpdf.awt.geom.Rectangle;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class Display extends Activity implements View.OnClickListener {
    private static final String     TAG         = "OpenLens::Display";
    MenuItem                        mSend       = null;
    MenuItem                        mView       = null;
    MenuItem                        mEdit       = null;
    MenuItem                        mSetSize    = null;
    Bitmap                          mBmp        = null;
    com.itextpdf.text.Rectangle     mPageSize   = PageSize.LETTER;
    private static final int        SEND        = 0;

    /**
     * Called when the activity is first created.
     */
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_display);

        ImageView imageView = (ImageView) findViewById(R.id.imageView);

        String image = getIntent().getStringExtra("image");
        try {
            mBmp = BitmapFactory.decodeStream(this.openFileInput(image));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        imageView.setOnClickListener(Display.this);

        imageView.setImageBitmap(mBmp);

        imageView.setVisibility(ImageView.VISIBLE);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SEND && resultCode == 0 && data == null)
            finish();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mSend = menu.add("Send To");
        mView = menu.add("View");
        mEdit = menu.add("Edit");
        mSetSize = menu.add("Set Size");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item == mEdit) {
            //TODO: Make editable box view
            return true;
        }
        else if ( item == mSetSize){
            List<String> names = new ArrayList<>();
            for(Field f : PageSize.class.getFields()) {
                names.add(f.getName());
            }
            names.add("Custom");
            final CharSequence[] charSequenceItems = names.toArray(new CharSequence[names.size()]);

            new AlertDialog.Builder(this)
                    .setTitle("Set Page Size")
                    .setItems(charSequenceItems, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == charSequenceItems.length - 1) {//Custom
                                new AlertDialog.Builder(Display.this)
                                        .setTitle("Custom Size")
                                        .setView(Display.this.getLayoutInflater().inflate(R.layout.dialog_custompagesize, null))
                                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                //FIXME
                                                Log.d(TAG, ((EditText) Display.this.findViewById(R.id.heightEditText)).getText().toString());
                                                Log.d(TAG, ((EditText) Display.this.findViewById(R.id.widthEditText)).getText().toString());
                                            }
                                        })
                                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                            }
                                        })
                                        .show();
                            }
                            else
                                mPageSize = PageSize.getRectangle(charSequenceItems[which].toString());
                        }
                    })
                    .show();

        }
        File file = new File(getFilesDir() + "/" + "output.pdf");
        try {
            Document document = new Document();
            document.setPageSize(mPageSize);
            //TODO:Test code

            if (mBmp.getWidth() > mBmp.getHeight())
                document.setPageSize(document.getPageSize().rotate());
            FileOutputStream fo = openFileOutput(file.getName(), Context.MODE_WORLD_READABLE);

            PdfWriter.getInstance(document, fo);
            document.open();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            mBmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
            Image i = Image.getInstance(stream.toByteArray(), true);
            i.setAbsolutePosition(0, 0);
            i.scaleAbsolute(document.getPageSize().getWidth(), document.getPageSize().getHeight());
            document.add(i);
            document.close();
            fo.close();
        } catch (IOException | DocumentException e) {
            e.printStackTrace();
        }

        if (item == mSend) {
            Intent pdf = new Intent(Intent.ACTION_SEND);
            pdf.setDataAndType(Uri.fromFile(file), "application/pdf");
            startActivityForResult(pdf, SEND);
            return true;
        } else if (item == mView) {
            Intent pdf = new Intent(Intent.ACTION_VIEW);
            pdf.setDataAndType(Uri.fromFile(file), "application/pdf");
            startActivity(pdf);
            return true;
        }

        return false;
    }

    public void onClick(View view) {

    }
}