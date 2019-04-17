package org.acestream.engine;

import android.os.Parcel;
import android.os.Parcelable;

public class Extension implements Parcelable {
	public String Name;
	public String Description;
	public String IssuedBy;
	public String Url;
	public boolean Enabled;
	public long ValidFrom;
	public long ValidTo;
	
	public Extension() {
	}
	private Extension(Parcel parcel) {
		readFromParcel(parcel);
	}
	@Override
	public String toString() {
		return Name;
	}
	@Override
	public int describeContents() {
		return 0;
	}
	@Override
	public void writeToParcel(Parcel parcel, int flags) {
		parcel.writeString(Name);
		parcel.writeString(Description);
		parcel.writeString(IssuedBy);
		parcel.writeString(Url);
		parcel.writeByte((byte)(Enabled ? 1 : 0));
		parcel.writeLong(ValidFrom);
		parcel.writeLong(ValidTo);
	}
	public void readFromParcel(Parcel parcel) {
		Name = parcel.readString();
		Description = parcel.readString();
		IssuedBy = parcel.readString();
		Url = parcel.readString();
		Enabled = parcel.readByte() == 1;
		ValidFrom = parcel.readLong();
		ValidTo = parcel.readLong();
	}
	public static final Parcelable.Creator<Extension> CREATOR = new Parcelable.Creator<Extension>() {
		public Extension createFromParcel(Parcel in) {
			return new Extension(in);
	    }
	    public Extension[] newArray(int size) {
	    	return new Extension[size];
	    }
	};
}

