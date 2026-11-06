package org.hmarheritage.ishkeyboard;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        android.widget.TextView tvDetails = findViewById(R.id.tv_about_project_details);
        if (tvDetails != null) {
            tvDetails.setText("• Maintained by: Hmar Heritage Foundation\n• Project Direction: Donal Muolhoi & Stephen Huolngo\n• Website: hmarheritage.pages.dev\n• Open Source: github.com/hmar-heritage-org\n• Contact: donalmuolhoi@gmail.com\n• Version: " + BuildConfig.VERSION_NAME + "\n• Lexicon: 64,813 words, 101,978 phrase transitions");
        }

        android.widget.Button btnVisit = findViewById(R.id.btn_visit_website);
        if (btnVisit != null) {
            btnVisit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        android.content.Intent intent = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://hmarheritage.pages.dev")
                        );
                        startActivity(intent);
                    } catch (Exception ignored) {}
                }
            });
        }
    }
}
