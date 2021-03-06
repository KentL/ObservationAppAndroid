package DBContract;

import android.provider.BaseColumns;

/**
 * Created by zhuol on 2/27/2016.
 */
public final class ObservationContract {
    public ObservationContract(){}

    public static abstract class NewestObservationEntry implements BaseColumns {
        public static final String TABLE_NAME = "newest_observation";
        public static final String COLUMN_NAME_NODE_ID = "nid";
        public static final String COLUMN_NAME_TITLE = "title";
        public static final String COLUMN_NAME_PHOTO_SERVER_URI = "photoServerUri";
        public static final String COLUMN_NAME_DATE= "date";
        public static final String COLUMN_NAME_PHOTO_LOCAL_URI= "photoLocalUri";
        public static final String COLUMN_NAME_AUTHOR= "author";
    }
    public static abstract class MyObservationEntry implements BaseColumns {
        public static final String TABLE_NAME = "my_observation";
        public static final String COLUMN_NAME_NODE_ID = "nid";
        public static final String COLUMN_NAME_TITLE = "title";
        public static final String COLUMN_NAME_PHOTO_SERVER_URI = "photoServerUri";
        public static final String COLUMN_NAME_DATE = "date";
        public static final String COLUMN_NAME_PHOTO_LOCAL_URI = "photoLocalUri";
    }
}
