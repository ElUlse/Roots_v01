package com.example.roots_d01;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color; // Added for placeholder color
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Change the generic type here to the base ViewHolder
public class JourneyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // --- View Type Constants ---
    private static final int VIEW_TYPE_JOURNEY = 0;
    private static final int VIEW_TYPE_HEADER = 1;

    // Use List<Object> to hold both Strings (headers) and JourneyDetails
    private List<Object> listItems;
    private final Context context;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private final OnJourneyActionListener actionListener;
    private Map<String, Boolean> headerExpansionStates; // ADDED: To hold expansion states


    // --- Interface for Activity communication ---
    public interface OnJourneyActionListener {
        void onRenameRequested(JourneyDetails journey);
        void onHeaderClicked(String headerDate);
    }

    // --- Constructor ---
    // Accepts List<Object>
    public JourneyAdapter(Context context, List<Object> items, OnJourneyActionListener listener) {
        this.context = context;
        this.listItems = (items != null) ? new ArrayList<>(items) : new ArrayList<>();
        this.actionListener = listener;
        this.headerExpansionStates = new HashMap<>(); // Initialize, will be updated
        Log.d("JourneyAdapter", "Adapter created with " + this.listItems.size() + " initial items.");
    }

    // Inside JourneyAdapter.java class

    public Object getItemForLog(int position) {
        if (listItems != null && position >= 0 && position < listItems.size()) {
            return listItems.get(position);
        }
        return null;
    }

    // --- getItemViewType ---
    // Determines if item is a Header (String) or a Journey (JourneyDetails)
    @Override
    public int getItemViewType(int position) {
        if (position < 0 || position >= listItems.size()) {
            Log.e("JourneyAdapter", "getItemViewType: Invalid position: " + position);
            return VIEW_TYPE_JOURNEY; // Fallback
        }
        Object item = listItems.get(position);
        if (item instanceof String) {
            return VIEW_TYPE_HEADER;
        } else if (item instanceof JourneyDetails) {
            return VIEW_TYPE_JOURNEY;
        }
        Log.w("JourneyAdapter", "getItemViewType: Unknown item type at position " + position);
        return VIEW_TYPE_JOURNEY; // Fallback
    }

    // --- onCreateViewHolder ---
    // Inflates the correct layout based on viewType
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View headerView = inflater.inflate(R.layout.list_item_date_header, parent, false);
            // MODIFIED: Pass actionListener to HeaderViewHolder
            return new HeaderViewHolder(headerView, actionListener);
        } else {
            View journeyView = inflater.inflate(R.layout.list_item_journey, parent, false);
            return new JourneyViewHolder(journeyView);
        }
    }

    // --- onBindViewHolder ---
    // Binds data to the correct ViewHolder type
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position < 0 || position >= listItems.size()) {
            Log.e("JourneyAdapter", "onBindViewHolder: Invalid position: " + position);
            return;
        }
        Object item = listItems.get(position);
        int viewType = getItemViewType(position);

        try {
            if (viewType == VIEW_TYPE_HEADER) {
                HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
                String dateText = (String) item;
                // MODIFIED: Get expansion state
                boolean isExpanded = headerExpansionStates.getOrDefault(dateText, true); // Default to expanded
                headerHolder.bind(dateText, isExpanded);
            } else if (viewType == VIEW_TYPE_JOURNEY) {
                JourneyViewHolder journeyHolder = (JourneyViewHolder) holder;
                JourneyDetails journeyDetails = (JourneyDetails) item;
                journeyHolder.bindJourney(journeyDetails, context, backgroundExecutor, mainThreadHandler, position);
            } else {
                Log.w("JourneyAdapter", "onBindViewHolder: Unknown view type " + viewType + " at position " + position);
            }
        } catch (ClassCastException e) {
            Log.e("JourneyAdapter", "onBindViewHolder: Error casting item at position " + position, e);
        } catch (Exception e) {
            Log.e("JourneyAdapter", "onBindViewHolder: Unexpected error binding view for position " + position, e);
        }
    }


    // --- getItemCount ---
    @Override
    public int getItemCount() {
        return listItems != null ? listItems.size() : 0;
    }

    // --- Method to update data ---
    public void updateJourneys(List<Object> newItems, Map<String, Boolean> expansionStates) {
        this.listItems = (newItems != null) ? new ArrayList<>(newItems) : new ArrayList<>();
        this.headerExpansionStates = (expansionStates != null) ? new HashMap<>(expansionStates) : new HashMap<>();
        Log.d("JourneyAdapter", "Adapter updated with " + this.listItems.size() + " new items. Expansion states count: " + this.headerExpansionStates.size());
        notifyDataSetChanged(); // Consider using DiffUtil later
    }


    // ========================================================================================
    //                             View Holder Classes
    // ========================================================================================

    // --- Header ViewHolder ---
    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateHeader;
        ImageView ivExpansionIndicator;
        OnJourneyActionListener listener;

        public HeaderViewHolder(@NonNull View itemView, OnJourneyActionListener listener) { // MODIFIED
            super(itemView);
            this.listener = listener; // ADDED
            tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
            ivExpansionIndicator = itemView.findViewById(R.id.ivExpansionIndicator); // ADDED

            if (tvDateHeader == null || ivExpansionIndicator == null) {
                Log.e("JourneyAdapter", "HeaderViewHolder: tvDateHeader or ivExpansionIndicator not found!");
            }
        }

        public void bind(String dateText, boolean isExpanded) {
            if (tvDateHeader != null) {
                tvDateHeader.setText(dateText);
            }
            if (ivExpansionIndicator != null) {
                // SET icon based on isExpanded state
                ivExpansionIndicator.setImageResource(isExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
            }

            // ADDED: Set click listener for the entire header item
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onHeaderClicked(dateText); // Notify activity
                }
            });
        }
    }

    // --- Journey ViewHolder ---
    // Made non-static so it can access adapter's context, listItems, listener etc. if needed
    // OR pass them via constructor/bind method
    class JourneyViewHolder extends RecyclerView.ViewHolder {
        ImageView mapPreviewImageView;
        TextView journeyNameTextView;
        TextView journeyDetailsTextView;
        TextView accuracyTextView;
        // Optional individual views (declare if used)
        // TextView startTimeTextView, durationTextView, distanceTextView, modeTextView;

        JourneyViewHolder(View itemView) {
            super(itemView);
            mapPreviewImageView = itemView.findViewById(R.id.ivMapPreview);
            journeyNameTextView = itemView.findViewById(R.id.journeyNameTextView);
            journeyDetailsTextView = itemView.findViewById(R.id.tvJourneyDetails);
            accuracyTextView = itemView.findViewById(R.id.tv_journey_item_accuracy);
            // Find other TextViews if they exist in your R.layout.list_item_journey

            // --- Click Listener ---
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listItems != null && position < listItems.size()) {
                    Object item = listItems.get(position);
                    if (item instanceof JourneyDetails) { // Check if it's a journey
                        JourneyDetails clickedJourney = (JourneyDetails) item;
                        Log.d("JourneyAdapterClick", "Item clicked! Position: " + position + ", StartTime: " + clickedJourney.startTimeMs);
                        Intent intent = new Intent(context, MainActivity.class);
                        intent.putExtra(MainActivity.EXTRA_SELECTED_JOURNEY_START_TIME, clickedJourney.startTimeMs);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        try {
                            context.startActivity(intent);
                            Log.d("JourneyAdapterClick", "Started MainActivity.");
                            if (context instanceof Activity) {
                                ((Activity) context).finish(); // Finish JourneyListActivity
                            }
                        } catch (Exception e) {
                            Log.e("JourneyAdapterClick", "Error starting MainActivity", e);
                        }
                    } else {
                        Log.w("JourneyAdapterClick", "Clicked item at position " + position + " is not JourneyDetails.");
                    }
                } else {
                    Log.w("JourneyAdapterClick", "Clicked item has NO_POSITION or list is invalid.");
                }
            });

            // --- Long Click Listener ---
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && actionListener != null && listItems != null && position < listItems.size()) {
                    Object item = listItems.get(position);
                    if (item instanceof JourneyDetails) {
                        JourneyDetails longClickedJourney = (JourneyDetails) item;
                        actionListener.onRenameRequested(longClickedJourney); // Uses adapter's listener
                        return true;
                    }
                }
                return false;
            });
        }

        // --- Moved binding logic here ---
        void bindJourney(JourneyDetails journeyDetails, Context ctx, ExecutorService bgExecutor, Handler uiHandler, int position) {
            // --- Set Text Data ---
            journeyNameTextView.setText(journeyDetails.journeyName != null ? journeyDetails.journeyName : "Journey");
            String detailsText = String.format("Start: %s | End: %s | Dist: %s",
                    journeyDetails.getFormattedStartTime(),
                    journeyDetails.getFormattedEndTime(),
                    journeyDetails.getFormattedDistance());
            journeyDetailsTextView.setText(detailsText);
            accuracyTextView.setText(journeyDetails.getFormattedAverageAccuracy());

            // --- Map Preview ---
            if (mapPreviewImageView != null) {
                mapPreviewImageView.setImageResource(0); // Clear placeholder
                mapPreviewImageView.setBackgroundColor(Color.LTGRAY); // Placeholder background
            }
            if (journeyDetails.points != null && journeyDetails.points.size() > 1 && mapPreviewImageView != null) {
                final List<PolylinePoint> points = journeyDetails.points;
                final int previewColor = JourneyListActivity.getColorForTransportMode(ctx, journeyDetails.getDominantMode());
                final int imageSizePx = dpToPx(80); // Use helper method

                bgExecutor.execute(() -> {
                    final Bitmap previewBitmap = MapPreviewGenerator.generatePreviewBitmap(
                            points, imageSizePx, imageSizePx, previewColor);
                    uiHandler.post(() -> {
                        // Check adapter position again before setting bitmap
                        if (getAdapterPosition() == position && mapPreviewImageView != null) {
                            if (previewBitmap != null) {
                                mapPreviewImageView.setBackgroundColor(Color.TRANSPARENT);
                                mapPreviewImageView.setImageBitmap(previewBitmap);
                            } else {
                                mapPreviewImageView.setBackgroundColor(Color.LTGRAY); // Reset placeholder on error
                            }
                        }
                    });
                });
            } else if (mapPreviewImageView != null) {
                mapPreviewImageView.setBackgroundColor(Color.LTGRAY); // Placeholder if no points/preview needed
            }
        }

        // Helper to convert dp to pixels
        private int dpToPx(int dp) {
            return Math.round((float) dp * context.getResources().getDisplayMetrics().density);
        }

    }
    // --- End of JourneyViewHolder ---

} // --- End of JourneyAdapter ---