package com.example.roots_d01;

import android.app.Activity; // For finishing activity
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set; // Import Set
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
// import java.util.concurrent.Executors; // Not directly used in adapter constructor

public class JourneyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "JourneyAdapter"; // Added TAG

    // --- View Type Constants ---
    private static final int VIEW_TYPE_JOURNEY = 0;
    private static final int VIEW_TYPE_DAY_HEADER = 1;
    private static final int VIEW_TYPE_WEEK_HEADER = 2;

    private List<Object> listItems;
    private final Context context;
    private final ExecutorService backgroundExecutor; // Passed from Activity
    private final Handler mainThreadHandler;       // Passed from Activity
    private final OnJourneyActionListener actionListener;
    private Map<String, Boolean> headerExpansionStates;


    public interface OnJourneyActionListener {
        void onRenameRequested(JourneyDetails journey);
        void onHeaderClicked(String headerDateText); // Parameter is the date string from DayHeaderItem
    }

    public JourneyAdapter(Context context, List<Object> items, OnJourneyActionListener listener, ExecutorService executor, Handler handler) {
        this.context = context;
        this.listItems = (items != null) ? new ArrayList<>(items) : new ArrayList<>();
        this.actionListener = listener;
        this.headerExpansionStates = new HashMap<>();
        this.backgroundExecutor = executor; // Store passed executor
        this.mainThreadHandler = handler;   // Store passed handler
        Log.d(TAG, "Adapter created with " + this.listItems.size() + " initial items.");
    }
    // Overloaded constructor for compatibility if executor/handler not passed immediately
    public JourneyAdapter(Context context, List<Object> items, OnJourneyActionListener listener) {
        this(context, items, listener, Executors.newSingleThreadExecutor(), new Handler(Looper.getMainLooper()));
        Log.w(TAG, "Adapter created using default backgroundExecutor and mainThreadHandler. Consider passing them for better resource management.");
    }


    public Object getItemForLog(int position) {
        if (listItems != null && position >= 0 && position < listItems.size()) {
            return listItems.get(position);
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        if (position < 0 || position >= listItems.size()) {
            Log.e(TAG, "getItemViewType: Invalid position: " + position);
            return VIEW_TYPE_JOURNEY;
        }
        Object item = listItems.get(position);
        if (item instanceof String) { // Week Headers are still Strings
            String headerText = (String) item;
            if (headerText.startsWith("Week of")) {
                return VIEW_TYPE_WEEK_HEADER;
            } else {
                // This case should ideally not happen if Day Headers are DayHeaderItem
                // However, if a plain string is somehow passed for a day, treat as day.
                Log.w(TAG, "getItemViewType: Item is a String but not 'Week of...'. Treating as DayHeader. Text: " + headerText);
                return VIEW_TYPE_DAY_HEADER; // Fallback for string that isn't a week
            }
        } else if (item instanceof DayHeaderItem) { // *** Day Headers are now DayHeaderItem objects ***
            return VIEW_TYPE_DAY_HEADER;
        } else if (item instanceof JourneyDetails) {
            return VIEW_TYPE_JOURNEY;
        }
        Log.w(TAG, "getItemViewType: Unknown item type at position " + position + ", class: " + item.getClass().getName());
        return VIEW_TYPE_JOURNEY;
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
        } else { // VIEW_TYPE_JOURNEY
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

        try {
            if (holder.getItemViewType() == VIEW_TYPE_WEEK_HEADER) {
                WeekHeaderViewHolder weekHolder = (WeekHeaderViewHolder) holder;
                String weekDateText = (String) item; // Week headers are still strings
                weekHolder.bind(weekDateText);
            } else if (holder.getItemViewType() == VIEW_TYPE_DAY_HEADER) {
                DayHeaderViewHolder dayHolder = (DayHeaderViewHolder) holder;
                DayHeaderItem dayHeaderItem = (DayHeaderItem) item; // Day headers are DayHeaderItem
                // Use dayHeaderItem.dateHeaderText as the key for expansion state
                boolean isExpanded = headerExpansionStates.getOrDefault(dayHeaderItem.dateHeaderText, true);
                dayHolder.bind(dayHeaderItem, isExpanded); // Pass the DayHeaderItem
            } else if (holder.getItemViewType() == VIEW_TYPE_JOURNEY) {
                JourneyViewHolder journeyHolder = (JourneyViewHolder) holder;
                JourneyDetails journeyDetails = (JourneyDetails) item;
                journeyHolder.bindJourney(journeyDetails, context, backgroundExecutor, mainThreadHandler, position);
            } else {
                Log.w(TAG, "onBindViewHolder: Unknown view type " + holder.getItemViewType() + " at position " + position);
            }
        } catch (ClassCastException e) {
            Log.e(TAG, "onBindViewHolder: Error casting item at position " + position + " to expected type. Item class: " + item.getClass().getName() + ", Holder type: " + holder.getClass().getName(), e);
        } catch (Exception e) {
            Log.e(TAG, "onBindViewHolder: Unexpected error binding view for position " + position, e);
        }
    }


    @Override
    public int getItemCount() {
        return listItems != null ? listItems.size() : 0;
    }

    public void updateJourneys(List<Object> newItems, Map<String, Boolean> expansionStates) {
        this.listItems = (newItems != null) ? new ArrayList<>(newItems) : new ArrayList<>();
        this.headerExpansionStates = (expansionStates != null) ? new HashMap<>(expansionStates) : new HashMap<>();
        Log.d(TAG, "Adapter updated with " + this.listItems.size() + " new items. Expansion states count: " + this.headerExpansionStates.size());
        notifyDataSetChanged();
    }


    // ========================================================================================
    //                             View Holder Classes
    // ========================================================================================

    public static class WeekHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvWeekDateHeader;

        public WeekHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvWeekDateHeader = itemView.findViewById(R.id.tvWeekDateHeader);
            if (tvWeekDateHeader == null) {
                Log.e(TAG, "WeekHeaderViewHolder: tvWeekDateHeader not found!");
            }
        }

        public void bind(String weekDateText) {
            if (tvWeekDateHeader != null) {
                tvWeekDateHeader.setText(weekDateText);
            }
        }
    }

    public static class DayHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateHeader;
        ImageView ivExpansionIndicator;
        LinearLayout llTransportModeIcons; // *** ADDED: Container for icons ***
        OnJourneyActionListener listener;

        public DayHeaderViewHolder(@NonNull View itemView, OnJourneyActionListener listener) {
            super(itemView);
            this.listener = listener;
            tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
            ivExpansionIndicator = itemView.findViewById(R.id.ivExpansionIndicator);
            llTransportModeIcons = itemView.findViewById(R.id.llTransportModeIcons); // *** Find the LinearLayout ***

            if (tvDateHeader == null || ivExpansionIndicator == null || llTransportModeIcons == null) {
                Log.e(TAG, "DayHeaderViewHolder: One or more views not found! tvDateHeader=" + (tvDateHeader==null) +
                        ", ivExpansionIndicator=" + (ivExpansionIndicator==null) +
                        ", llTransportModeIcons=" + (llTransportModeIcons==null) );
            }
        }

        // *** MODIFIED bind method for DayHeaderViewHolder ***
        public void bind(DayHeaderItem dayHeaderItem, boolean isExpanded) {
            if (tvDateHeader != null) {
                tvDateHeader.setText(dayHeaderItem.dateHeaderText);
            }
            if (ivExpansionIndicator != null) {
                ivExpansionIndicator.setImageResource(isExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
                ivExpansionIndicator.setVisibility(View.VISIBLE);
            }

            if (llTransportModeIcons != null) {
                llTransportModeIcons.removeAllViews(); // Clear previous icons
                if (dayHeaderItem.transportModes != null && !dayHeaderItem.transportModes.isEmpty()) {
                    // Sort modes for consistent display order (optional but good UX)
                    List<String> sortedModes = new ArrayList<>(dayHeaderItem.transportModes);
                    Collections.sort(sortedModes); // Alphabetical sort

                    for (String mode : sortedModes) {
                        ImageView iconView = new ImageView(itemView.getContext());
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                dpToPx(18), // Icon size (e.g., 18dp)
                                dpToPx(18)
                        );
                        params.setMarginEnd(dpToPx(4)); // Margin between icons
                        iconView.setLayoutParams(params);

                        int iconResId = getDrawableResourceIdForMode(mode);
                        if (iconResId != 0) { // 0 if no icon defined for a mode
                            iconView.setImageDrawable(ContextCompat.getDrawable(itemView.getContext(), iconResId));
                            // Optional: Set tint if your icons are single color and need theming
                            // iconView.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(itemView.getContext(), R.color.your_icon_tint_color)));
                            llTransportModeIcons.addView(iconView);
                        }
                    }
                    llTransportModeIcons.setVisibility(View.VISIBLE);
                } else {
                    llTransportModeIcons.setVisibility(View.GONE); // Hide if no modes
                }
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    // Pass the dateHeaderText string for toggling expansion
                    listener.onHeaderClicked(dayHeaderItem.dateHeaderText);
                }
            });
        }

        // Helper to convert dp to pixels (should be in a utility class or base ViewHolder)
        private int dpToPx(int dp) {
            return Math.round((float) dp * itemView.getContext().getResources().getDisplayMetrics().density);
        }

        // Helper to get drawable resource ID for transport mode
        private int getDrawableResourceIdForMode(String mode) {
            if (mode == null) return 0;
            switch (mode) {
                case "Walking":
                    return R.drawable.ic_walking; // Ensure you have this drawable
                case "Bicycling":
                    return R.drawable.ic_directions_bike; // Ensure you have this
                case "In Vehicle":
                    return R.drawable.ic_directions_in_vehicle; // Ensure you have this
                // Add cases for other modes if necessary
                default:
                    return 0; // No icon for unknown or unhandled modes
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
                        Log.d("JourneyAdapterClick", "Item clicked! Position: " + position + ", StartTime: " + clickedJourney.startTimeMs);
                        Intent intent = new Intent(context, MainActivity.class);
                        intent.putExtra(MainActivity.EXTRA_SELECTED_JOURNEY_START_TIME, clickedJourney.startTimeMs);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        try {
                            context.startActivity(intent);
                            Log.d("JourneyAdapterClick", "Started MainActivity.");
                            if (context instanceof Activity) {
                                ((Activity) context).finish();
                            }
                        } catch (Exception e) {
                            Log.e("JourneyAdapterClick", "Error starting MainActivity", e);
                        }
                    } else {
                        Log.w("JourneyAdapterClick", "Clicked item at position " + position + " is not JourneyDetails.");
                    }
                } else {
                    Log.w("JourneyAdapterClick", "Clicked item has NO_POSITION or list is invalid. Position: " + position);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && actionListener != null && listItems != null && position < listItems.size()) {
                    Object item = listItems.get(position);
                    if (item instanceof JourneyDetails) {
                        JourneyDetails longClickedJourney = (JourneyDetails) item;
                        actionListener.onRenameRequested(longClickedJourney);
                        return true;
                    }
                }
                return false;
            });
        }

        void bindJourney(JourneyDetails journeyDetails, Context ctx, ExecutorService bgExecutor, Handler uiHandler, int position) {
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
            }
            if (journeyDetails.points != null && journeyDetails.points.size() > 1 && mapPreviewImageView != null) {
                final List<PolylinePoint> points = journeyDetails.points;
                final int previewColor = JourneyListActivity.getColorForTransportMode(ctx, journeyDetails.getDominantMode());
                final int imageSizePx = dpToPx(80);

                bgExecutor.execute(() -> {
                    final Bitmap previewBitmap = MapPreviewGenerator.generatePreviewBitmap(
                            points, imageSizePx, imageSizePx, previewColor);
                    uiHandler.post(() -> {
                        if (getBindingAdapterPosition() == position && mapPreviewImageView != null) {
                            if (previewBitmap != null) {
                                mapPreviewImageView.setBackgroundColor(Color.TRANSPARENT);
                                mapPreviewImageView.setImageBitmap(previewBitmap);
                            } else {
                                mapPreviewImageView.setBackgroundColor(Color.LTGRAY);
                            }
                        }
                    });
                });
            } else if (mapPreviewImageView != null) {
                mapPreviewImageView.setBackgroundColor(Color.LTGRAY);
            }
        }
        private int dpToPx(int dp) {
            return Math.round((float) dp * context.getResources().getDisplayMetrics().density);
        }
    }
}
