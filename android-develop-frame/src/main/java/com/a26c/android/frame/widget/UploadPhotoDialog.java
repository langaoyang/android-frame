package com.a26c.android.frame.widget;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.View;

import com.a26c.android.frame.R;
import com.a26c.android.frame.util.FrameBitmapUtil;
import com.a26c.android.frame.util.FrameCropUtils;
import com.a26c.android.frame.util.DialogFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;


/**
 * 封装一个获取图片的工具类，
 * showDialog（）方法调用后弹出选择相机或者相册的dialog，
 * onActivityResult（）方法装饰了相关操作，可以直接在对应Activity的OnActivityResult的方法里调用，
 *
 * @author gl
 */
public class UploadPhotoDialog {

    private static final byte RESULT_CAMERA = 12;
    private static final byte RESULT_ALBUM = 13;
    private static final byte RESULT_ZOOM_PHOTO = 14;

    public static final byte PHOTO = 1;
    public static final byte ALBUM = 2;
    public static final byte PHOTO_AND_ALBUM = 3;

    /**
     * 默认相册和拍照的入口都有
     */
    private int type = PHOTO_AND_ALBUM;

    /**
     * 想要获取图片的大小
     */
    private int imageSize = 100;
    /**
     * 裁剪出来的图片的高
     */
    private int HEIGHT = 500;
    /**
     * 图片的高
     */
    public int WIDTH;


    private AlertDialog dialog;
    private DialogListener listener;
    private Context context;
    /**
     * 这个值等于0表示获取到的图片无需压缩
     */
    private float radio = 0;

    /**
     * 压缩图片后保存至此file
     */
    private File outFile;

    /**
     * @param context
     * @param ratio_  想要获取图片的宽高比，必传！传0表示不进行压缩
     * @param l       点击按钮的监听 ，可以设为空，那么就默认打开相机和相册的操作
     */
    public UploadPhotoDialog(Context context, float ratio_, DialogListener l) {
        this.context = context;
        radio = ratio_;
        listener = l;
        WIDTH = (int) (HEIGHT * ratio_);
        createFile();
    }

    /**
     * 对外暴露一个显示dialog的方法
     */
    public void showDialog() {
        View view = View.inflate(context, R.layout.frame_layout_upload_photo_dialog, null);
        View photoLayout = view.findViewById(R.id.photoLayout);
        View albumLayout = view.findViewById(R.id.albumLayout);
        photoLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(new File(Environment.getExternalStorageDirectory(), "temp.jpg")));
                ((Activity) context).startActivityForResult(intent, RESULT_CAMERA);
                if (listener != null) {
                    listener.photoClick();
                }
                dialog.dismiss();
            }
        });
        albumLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK, null);
                intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
                ((Activity) context).startActivityForResult(intent, RESULT_ALBUM);
                if (listener != null) {
                    listener.albumClick();
                }
                dialog.dismiss();
            }
        });
        if (type == PHOTO) {
            albumLayout.setVisibility(View.GONE);
        }
        if (type == ALBUM) {
            photoLayout.setVisibility(View.GONE);
        }
        dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        dialog.show();

    }


    public interface DialogListener {
        void photoClick();

        void albumClick();

        /**
         * 从相册或者相机刚刚获取到图片时
         */
        void receiveImage();

    }

    /**
     * 压缩图片后获取图片成功的接口
     */
    public interface OnGetImageSuccessListener {
        /**
         * @param bitmap    拍照或者从图库拿到并且压缩过后的bitmap对象
         * @param imagePath 图像被保存到本地的地址
         */
        void success(Bitmap bitmap, String imagePath);

    }


    /**
     * dialog消失时的操作
     */
    public void setCancelListener(OnCancelListener l) {
        dialog.setOnCancelListener(l);
    }


    // 拍完照片的回调方法
    public void onActivityResult(int requestCode, int resultCode, final Intent data, final OnGetImageSuccessListener l) {
        if (resultCode != Activity.RESULT_OK)
            return;
        switch (requestCode) {
            case RESULT_CAMERA:
                if (listener != null) listener.receiveImage();
                Observable.just(1)
                        .map(new Func1<Integer, HashMap<String, Object>>() {
                            @Override
                            public HashMap<String, Object> call(Integer integer) {
                                //如果需要压缩
                                if (radio != 0) {
                                    File picture2 = new File(Environment.getExternalStorageDirectory() + "/temp.jpg");
                                    ZoomPhoto(Uri.fromFile(picture2));
                                } else {
                                    Bitmap bitmap = BitmapFactory.decodeFile(Environment.getExternalStorageDirectory() + "/temp.jpg");
                                    return saveBitmap(bitmap);
                                }
                                return null;
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.io())
                        .subscribe(new Action1<HashMap<String, Object>>() {
                            @Override
                            public void call(HashMap<String, Object> map) {
                                if (map != null && l != null) {
                                    l.success((Bitmap) map.get("bitmap"), (String) map.get("filePath"));
                                }
                            }
                        });


                break;

            case RESULT_ALBUM:
                if (listener != null) listener.receiveImage();
                Observable.just(1)
                        .map(new Func1<Integer, HashMap<String, Object>>() {
                            @Override
                            public HashMap<String, Object> call(Integer integer) {
                                if (radio != 0) {
                                    ZoomPhoto(data.getData());
                                } else {
                                    Bitmap bitmap = FrameBitmapUtil.getBitmapFromUri(context, data.getData());
                                    if (bitmap == null) {
                                        DialogFactory.show(context, "提示", "未知错误类型", "确定", null);
                                        return null;
                                    }
                                    return saveBitmap(bitmap);
                                }
                                return null;
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.io())
                        .subscribe(new Action1<HashMap<String, Object>>() {
                            @Override
                            public void call(HashMap<String, Object> map) {
                                if (map != null && l != null) {
                                    l.success((Bitmap) map.get("bitmap"), (String) map.get("filePath"));
                                }
                            }
                        });
                break;

            // 压缩并保存图片
            case RESULT_ZOOM_PHOTO:
                Observable.just(1)
                        .map(new Func1<Integer, HashMap<String, Object>>() {
                            @Override
                            public HashMap<String, Object> call(Integer integer) {
                                Bitmap bitmap_Left = BitmapFactory.decodeFile(outFile.getAbsolutePath());
                                if (bitmap_Left == null) {
                                    DialogFactory.show(context, "提示", "获取图片失败,未获取操作文件权限", "确定", null);
                                    return null;
                                }
                                return saveBitmap(bitmap_Left);
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.io())
                        .subscribe(new Action1<HashMap<String, Object>>() {
                            @Override
                            public void call(HashMap<String, Object> map) {
                                if (map != null && l != null) {
                                    l.success((Bitmap) map.get("bitmap"), (String) map.get("filePath"));
                                }
                            }
                        });

                break;
            default:
                break;
        }

    }

    @NonNull
    private HashMap<String, Object> saveBitmap(Bitmap bitmap) {
        String fileName = System.currentTimeMillis() + "life.jpg";
        String filePath = FrameBitmapUtil.savePicture(context, fileName, bitmap, imageSize);
        HashMap<String, Object> map = new HashMap<>();
        map.put("bitmap", bitmap);
        map.put("filePath", filePath);
        return map;
    }

    // 调用系统压缩图片的工具类
    private void ZoomPhoto(Uri uri) {
        Intent intent = new Intent("com.android.camera.action.CROP");// 调用Android系统自带的一个图片剪裁页面,
        Uri newUri = Uri.parse("file://" + FrameCropUtils.getPath(context, uri));
        intent.setDataAndType(newUri, "image/*");
        intent.putExtra("crop", "true");// 进行修剪
        // aspectX aspectY 是宽高的比例
        intent.putExtra("aspectX", WIDTH);
        intent.putExtra("aspectY", HEIGHT);
        // outputX outputY 是裁剪图片宽高
        intent.putExtra("outputX", WIDTH);
        intent.putExtra("outputY", HEIGHT);
        intent.putExtra("scale", true);

        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(outFile));
        intent.putExtra("return-data", false);
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
        intent.putExtra("noFaceDetection", true);
        ((Activity) context).startActivityForResult(intent, RESULT_ZOOM_PHOTO);
    }

    private void createFile() {
        outFile = new File(Environment.getExternalStorageDirectory()
                .getAbsolutePath() + "/", "android_frame_scrop.jpg");
        if (!outFile.exists()) {
            try {
                outFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 设置保存之后的压缩的图片的大小
     */
    public void setImageSize(int image_size) {
        this.imageSize = image_size;
    }

    public void setType(int type) {
        this.type = type;
    }
}
