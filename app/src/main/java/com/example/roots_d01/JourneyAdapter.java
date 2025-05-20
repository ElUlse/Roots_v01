package com.example.roots_d01;

import android.app.Activity; // For finishing activity
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout; // Import LinearLayout
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat; // For drawable lookup
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set; // Make sure Set is imported if DayHeaderItem uses it
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JourneyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "JourneyAdapter";

    private static final int VIEW_TYPE_JOURNEY = 0;
    private static final int VIEW_TYPE_DAY_HEADER = 1;
    private static final int VIEW_TYPE_WEEK_HEADER = 2;

    private List<Object> listItems;
    private final Context context;
    private final ExecutorService backgroundExecutor;
    private final Handler mainThreadHandler;
    private final OnJourneyActionListener actionListener;
    private Map<String, Boolean> headerExpansionStates;
    private Map<Long, String> journeyPreviewFilePathsMap;


    public interface OnJourneyActionListener {
        void onRenameRequested(JourneyDetails journey);
        void onHeaderClicked(String headerDateText);
    }

    public JourneyAdapter(Context context, List<Object> items, OnJourneyActionListener listener, ExecutorService executor, Handler handler) {
        this.context = context;
        this.listItems = (items != null) ? new ArrayList<>(items) : new ArrayList<>();
        this.actionListener = listener;
        this.headerExpansionStates = new HashMap<>();
        this.journeyPreviewFilePathsMap = new HashMap<>();
        this.backgroundExecutor = executor; // Use passed executor
        this.mainThreadHandler = handler;   // Use passed handler
        Log.d(TAG, "Adapter created with " + this.listItems.size() + " initial items using provided Executor/Handler.");
    }

    // This overloaded constructor is fine for cases where JourneyListActivity might not pass them,
    // but JourneyListActivity *should* be passing its own instances.
    public JourneyAdapter(Context context, List<Object> items, OnJourneyActionListener listener) {
        this(context, items, listener, Executors.newSingleThreadExecutor(), new Handler(Looper.getMainLooper()));
        Log.w(TAG, "Adapter created using NEW default backgroundExecutor and mainThreadHandler.");
    }

    public void updateJourneysWithPreviews(List<Object> newItems, Map<String, Boolean> expansionStates, Map<Long, String> previewPaths) {
        this.listItems = (newItems != null) ? new ArrayList<>(newItems) : new ArrayList<>();
        this.headerExpansionStates = (expansionStates != null) ? new HashMap<>(expansionStates) : new HashMap<>();
        this.journeyPreviewFilePathsMap = (previewPaths != null) ? new HashMap<>(previewPaths) : new HashMap<>();
        Log.d(TAG, "Adapter updated. Items: " + this.listItems.size() + ", ExpansionStates: " + this.headerExpansionStates.size() + ", PreviewPaths: " + this.journeyPreviewFilePathsMap.size());
        notifyDataSetChanged();
    }

    // Deprecated, but kept for safety if any old calls exist.
    public void updateJourneys(List<Object> newItems, Map<String, Boolean> expansionStates) {
        updateJourneysWithPreviews(newItems, expansionStates, new HashMap<>());
    }

    @Override
    public int getItemViewType(int position) {
        if (position < 0 || position >= listItems.size()) {
            Log.e(TAG, "getItemViewType: Invalid position: " + position + ", list size: " + listItems.size());
            return VIEW_TYPE_JOURNEY; // Default to prevent crash, but indicates an issue
        }
        Object item = listItems.get(position);
        if (item == null) {
            Log.e(TAG, "getItemViewType: Item at position " + position + " is null!");
            return VIEW_TYPE_JOURNEY; // Handle null item gracefully
        }
        if (item instanceof String) {
            String headerText = (String) item;
            if (headerText.startsWith("Week of")) {
                return VIEW_TYPE_WEEK_HEADER;
            } else {
                Log.w(TAG, "getItemViewType: Item is a String but not 'Week of...'. Text: " + headerText);
                return VIEW_TYPE_DAY_HEADER; // Assuming other strings are day headers
            }
        } else if (item instanceof DayHeaderItem) {
            return VIEW_TYPE_DAY_HEADER;
        } else if (item instanceof JourneyDetails) {
            return VIEW_TYPE_JOURNEY;
        }
        Log.w(TAG, "getItemViewType: Unknown item type at position " + position + ", class: " + item.getClass().getName());
        return VIEW_TYPE_JOURNEY; // Default for unknown types
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_WEEK_HEADER) {
            View weekHeaderView = inflater.inflate(R.layout.list_item_week_header, parent, false);
            return new WeekHeaderViewHolder(weekHeaderView);
        } else if (viewType == VIEW_TYPE_DAY_HEADER) {
            View dayHeaderView = inflater.inflate(R.layout.list_item_date_header, parent, false);
            return new DayHeaderViewHolder(dayHeaderView, actionListener);
        } else { // VIEW_TYPE_JOURNEY or default/error case
            View journeyView = inflater.inflate(R.layout.list_item_journey, parent, false);
            return new JourneyViewHolder(journeyView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position < 0 || position >= listItems.size()) {
            Log.e(TAG, "onBindViewHolder: Invalid position: " + position);
            return;
        }
        Object item = listItems.get(position);
        if (item == null) {
            Log.e(TAG, "onBindViewHolder: Item at position " + position + " is null. Cannot bind.");
            // Optionally, clear the holder's views or set them to a default/error state
            return;
        }

        try {
            if (holder.getItemViewType() == VIEW_TYPE_WEEK_HEADER) {
                ((WeekHeaderViewHolder) holder).bind((String) item);
            } else if (holder.getItemViewType() == VIEW_TYPE_DAY_HEADER) {
                DayHeaderItem dayHeaderItem = (DayHeaderItem) item;
                boolean isExpanded = headerExpansionStates.getOrDefault(dayHeaderItem.dateHeaderText, false); // Default to not expanded if not found
                ((DayHeaderViewHolder) holder).bind(dayHeaderItem, isExpanded);
            } else if (holder.getItemViewType() == VIEW_TYPE_JOURNEY) {
                // Pass the adapter's context, backgroundExecutor, and mainThreadHandler
                ((JourneyViewHolder) holder).bindJourney((JourneyDetails) item, this.context, this.backgroundExecutor, this.mainThreadHandler, position);
            }
        } catch (ClassCastException e) {
            Log.e(TAG, "onBindViewHolder: ClassCastException at position " + position + ". Item: " + item.getClass().getName() + ", Holder: " + holder.getClass().getName(), e);
        } catch (Exception e) {
            Log.e(TAG, "onBindViewHolder: Unexpected error at position " + position, e);
        }
    }


    @Override
    public int getItemCount() {
        return listItems != null ? listItems.size() : 0;
    }

    public static class WeekHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvWeekDateHeader;
        public WeekHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvWeekDateHeader = itemView.findViewById(R.id.tvWeekDateHeader);
        }
        public void bind(String weekDateText) {
            if (tvWeekDateHeader != null) tvWeekDateHeader.setText(weekDateText);
        }
    }

    public static class DayHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateHeader;
        ImageView ivExpansionIndicator;
        LinearLayout llTransportModeIcons;
        OnJourneyActionListener listener;

        public DayHeaderViewHolder(@NonNull View itemView, OnJourneyActionListener listener) {
            super(itemView);
            this.listener = listener;
            tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
            ivExpansionIndicator = itemView.findViewById(R.id.ivExpansionIndicator);
            llTransportModeIcons = itemView.findViewById(R.id.llTransportModeIcons);
        }

        public void bind(DayHeaderItem dayHeaderItem, boolean isExpanded) {
            if (tvDateHeader != null) tvDateHeader.setText(dayHeaderItem.dateHeaderText);
            if (ivExpansionIndicator != null) {
                ivExpansionIndicator.setImageResource(isExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
            }

            if (llTransportModeIcons != null) {
                llTransportModeIcons.removeAllViews();
                if (dayHeaderItem.transportModes != null && !dayHeaderItem.transportModes.isEmpty()) {
                    List<String> sortedModes = new ArrayList<>(dayHeaderItem.transportModes);
                    Collections.sort(sortedModes);
                    for (String mode : sortedModes) {
                        ImageView iconView = new ImageView(itemView.getContext());
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(18), dpToPx(18));
                        params.setMarginEnd(dpToPx(4));
                        iconView.setLayoutParams(params);
                        int iconResId = getDrawableResourceIdForMode(mode);
                        if (iconResId != 0) {
                            iconView.setImageDrawable(ContextCompat.getDrawable(itemView.getContext(), iconResId));
                            llTransportModeIcons.addView(iconView);
                        }
                    }
                    llTransportModeIcons.setVisibility(View.VISIBLE);
                } else {
                    llTransportModeIcons.setVisibility(View.GONE);
                }
            }

            itemView.setOnClickListener(v -> {
                if (listener != null && dayHeaderItem != null) { // Check dayHeaderItem for null
                    listener.onHeaderClicked(dayHeaderItem.dateHeaderText);
                }
            });
        }
        private int dpToPx(int dp) { return Math.round((float) dp * itemView.getContext().getResources().getDisplayMetrics().density); }
        private int getDrawableResourceIdForMode(String mode) {
            if (mode == null) return 0;
            switch (mode) {
                case "Walking": return R.drawable.ic_walking;
                case "Bicycling": return R.drawable.ic_directions_bike;
                case "In Vehicle": return R.drawable.ic_directions_in_vehicle;
                default: return 0;
            }
        }
    }

    class JourneyViewHolder extends RecyclerView.ViewHolder {
        ImageView mapPreviewImageView;
        TextView journeyNameTextView;
        TextView journeyDetailsTextView;
        TextView accuracyTextView;

        JourneyViewHolder(View itemView) {
            super(itemView);
            mapPreviewImageView = itemView.findViewById(R.id.ivMapPreview);
            journeyNameTextView = itemView.findViewById(R.id.journeyNameTextView);
            journeyDetailsTextView = itemView.findViewById(R.id.tvJourneyDetails);
            accuracyTextView = itemView.findViewById(R.id.tv_journey_item_accuracy);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listItems != null && position < listItems.size()) {
                    Object item = listItems.get(position);
                    if (item instanceof JourneyDetails) {
                        JourneyDetails clickedJourney = (JourneyDetails) item;
                        Intent intent = new Intent(context, MainActivity.class);
                        intent.putExtra(MainActivity.EXTRA_SELECTED_JOURNEY_START_TIME, clickedJourney.startTimeMs);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        try {
                            context.startActivity(intent);
                            if (context instanceof Activity) {
                                ((Activity) context).finish();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error starting MainActivity from JourneyAdapter", e);
                        }
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && actionListener != null && listItems != null && position < listItems.size()) {
                    Object item = listItems.get(position);
                    if (item instanceof JourneyDetails) {
                        actionListener.onRenameRequested((JourneyDetails) item);
                        return true;
                    }
                }
                return false;
            });
        }

        void bindJourney(JourneyDetails journeyDetails, Context ctx, ExecutorService bgExecutor, Handler uiHandler, int position) {
            if (journeyDetails == null) {
                Log.e(TAG, "bindJourney called with null journeyDetails at position " + position);
                // Optionally clear views or show error state
                journeyNameTextView.setText("Error: Journey data missing");
                journeyDetailsTextView.setText("");
                accuracyTextView.setText("");
                if (mapPreviewImageView != null) mapPreviewImageView.setImageResource(0); // Clear image
                return;
            }

            journeyNameTextView.setText(journeyDetails.journeyName != null ? journeyDetails.journeyName : "Journey");
            String detailsText = String.format("Start: %s | End: %s | Dist: %s",
                    journeyDetails.getFormattedStartTime(),
                    journeyDetails.getFormattedEndTime(),
                    journeyDetails.getFormattedDistance());
            journeyDetailsTextView.setText(detailsText);
            accuracyTextView.setText(journeyDetails.getFormattedAverageAccuracy());

            if (mapPreviewImageView != null) {
                mapPreviewImageView.setImageResource(0);
                mapPreviewImageView.setBackgroundColor(Color.LTGRAY);

                String previewFilePath = null;
                if (journeyPreviewFilePathsMap != null) {
                    previewFilePath = journeyPreviewFilePathsMap.get(journeyDetails.startTimeMs);
                }

                if (previewFilePath != null && new File(previewFilePath).exists()) {
                    final String finalPreviewFilePath = previewFilePath;
                    bgExecutor.execute(() -> {
                        Bitmap previewBitmap = null;
                        try {
                            previewBitmap = BitmapFactory.decodeFile(finalPreviewFilePath);
                        } catch (OutOfMemoryError oom) {
                            Log.e(TAG, "OutOfMemoryError decoding preview file: " + finalPreviewFilePath, oom);
                            // Consider notifying UI or trying a smaller sample
                        } catch (Exception e) {
                            Log.e(TAG, "Exception decoding preview file: " + finalPreviewFilePath, e);
                        }

                        final Bitmap finalBitmap = previewBitmap; // Effectively final for lambda
                        uiHandler.post(() -> {
                            if (getBindingAdapterPosition() == position && mapPreviewImageView != null) { // Re-check position
                                if (finalBitmap != null) {
                                    mapPreviewImageView.setBackgroundColor(Color.TRANSPARENT);
                                    mapPreviewImageView.setImageBitmap(finalBitmap);
                                    Log.d(TAG, "Loaded pre-generated preview for journey " + journeyDetails.startTimeMs);
                                } else {
                                    Log.w(TAG, "Failed to decode/load preview image: " + finalPreviewFilePath);
                                    generateFallbackPreview(journeyDetails, ctx, bgExecutor, uiHandler, position);
                                }
                            }
                        });
                    });
                } else {
                    Log.d(TAG, "No pre-generated preview for journey " + journeyDetails.startTimeMs + ". Path: " + previewFilePath +". Generating fallback.");
                    generateFallbackPreview(journeyDetails, ctx, bgExecutor, uiHandler, position);
                }
            }
        }

        private void generateFallbackPreview(JourneyDetails journeyDetails, Context ctx, ExecutorService bgExecutor, Handler uiHandler, int position) {
            if (mapPreviewImageView == null || journeyDetails == null || journeyDetails.points == null || journeyDetails.points.size() < 2) {
                if (mapPreviewImageView != null) mapPreviewImageView.setBackgroundColor(Color.LTGRAY);
                Log.w(TAG, "generateFallbackPreview: Cannot generate, invalid input or view. Journey points: " + (journeyDetails !=null ? (journeyDetails.points != null ? journeyDetails.points.size() : "null") : "null_journey"));
                return;
            }

            final List<PolylinePoint> points = journeyDetails.points;
            final int previewColor = JourneyListActivity.getColorForTransportMode(ctx, journeyDetails.getDominantMode());
            final int fixedImageHeightPx = dpToPx(itemView.getContext(), 180);

            mapPreviewImageView.post(() -> {
                final int imageWidthPx = mapPreviewImageView.getWidth();
                if (imageWidthPx > 0) {
                    bgExecutor.execute(() -> {
                        final Bitmap fallbackBitmap = MapPreviewGenerator.generatePreviewBitmap(
                                points, imageWidthPx, fixedImageHeightPx, previewColor);
                        uiHandler.post(() -> {
                            if (getBindingAdapterPosition() == position && mapPreviewImageView != null) { // Re-check position
                                if (fallbackBitmap != null) {
                                    mapPreviewImageView.setBackgroundColor(Color.TRANSPARENT);
                                    mapPreviewImageView.setImageBitmap(fallbackBitmap);
                                } else {
                                    mapPreviewImageView.setBackgroundColor(Color.DKGRAY);
                                }
                            }
                        });
                    });
                } else {
                    Log.w(TAG, "generateFallbackPreview: mapPreviewImageView width is 0 at position " + position);
                    if (getBindingAdapterPosition() == position && mapPreviewImageView != null) {
                        mapPreviewImageView.setBackgroundColor(Color.DKGRAY);
                    }
                }
            });
        }

        private int dpToPx(Context context, int dp) {
            return Math.round((float) dp * context.getResources().getDisplayMetrics().density);
        }
    }
}
